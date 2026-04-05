#!/usr/bin/env python3
"""
Terminal application for displaying PostgreSQL database state.
Uses curses for terminal UI.
"""

import base64
import curses
import json
import re
import sys
import time
import urllib.error
import urllib.request
from typing import Dict, List, Tuple, Optional
from datetime import datetime

try:
    import psycopg2
    from psycopg2.extras import RealDictCursor
except ImportError:
    print("Error: psycopg2 is required. Install it with: pip install psycopg2-binary")
    sys.exit(1)


class ConfigParser:
    """Simple parser for HOCON-like config format."""
    
    @staticmethod
    def parse_config(filepath: str) -> Dict[str, Dict[str, str]]:
        """Parse application.conf file."""
        config = {}
        current_section = None
        
        with open(filepath, 'r') as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith('#'):
                    continue
                
                # Section header
                if line.endswith('{'):
                    current_section = line[:-1].strip()
                    config[current_section] = {}
                elif line == '}':
                    current_section = None
                elif current_section and '=' in line:
                    key, value = line.split('=', 1)
                    key = key.strip()
                    value = value.strip().strip('"')
                    config[current_section][key] = value
        
        return config


def http_post_json(
    url: str,
    payload: dict,
    timeout: float = 15.0,
    bearer: Optional[str] = None,
) -> Tuple[int, str]:
    """POST JSON body; returns (status_code, response_body_text)."""
    data = json.dumps(payload).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if bearer:
        headers["Authorization"] = f"Bearer {bearer}"
    req = urllib.request.Request(
        url,
        data=data,
        method="POST",
        headers=headers,
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.getcode(), resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace") if e.fp else ""
        return e.code, raw
    except urllib.error.URLError as e:
        return -1, str(e.reason if hasattr(e, "reason") else e)


def jwt_payload_dict(token: str) -> Optional[dict]:
    """Decode JWT payload (no signature verification; same issuer as login)."""
    try:
        parts = token.split(".")
        if len(parts) < 2:
            return None
        payload_b64 = parts[1]
        pad = "=" * ((4 - len(payload_b64) % 4) % 4)
        raw = base64.urlsafe_b64decode(payload_b64 + pad)
        return json.loads(raw.decode("utf-8"))
    except Exception:
        return None


def jwt_user_id(token: str) -> Optional[str]:
    d = jwt_payload_dict(token)
    if not d:
        return None
    uid = d.get("userId")
    if isinstance(uid, str) and uid.strip():
        return uid.strip()
    content = d.get("content")
    if isinstance(content, str):
        try:
            inner = json.loads(content)
            u = inner.get("userId")
            if isinstance(u, str) and u.strip():
                return u.strip()
        except json.JSONDecodeError:
            pass
    return None


class DatabaseConnection:
    """PostgreSQL database connection manager."""
    
    def __init__(self, config: Dict[str, str]):
        jdbc_url = config.get('jdbcUrl', '')
        # Parse JDBC URL: jdbc:postgresql://host:port/database
        match = re.match(r'jdbc:postgresql://([^:]+):(\d+)/(.*)', jdbc_url)
        if match:
            host, port, database = match.groups()
        else:
            # Fallback parsing
            match = re.match(r'jdbc:postgresql://([^:]+):(\d+)/?', jdbc_url)
            if match:
                host, port = match.groups()
                database = 'postgres'
            else:
                host = 'localhost'
                port = '5432'
                database = 'postgres'
        
        self.host = host
        self.port = int(port)
        self.database = database
        self.username = config.get('username', 'postgres')
        self.password = config.get('password', 'postgres')
        self.conn = None
        self.last_error = None
    
    def connect(self):
        """Establish database connection."""
        try:
            self.conn = psycopg2.connect(
                host=self.host,
                port=self.port,
                database=self.database,
                user=self.username,
                password=self.password
            )
            self.conn.autocommit = True
            self.last_error = None
            return True
        except Exception as e:
            self.last_error = str(e)
            return False
    
    def execute_query(self, query: str) -> Tuple[List[Dict], Optional[str]]:
        """Execute query and return (data, error). On connection failure, reconnect and retry once."""
        if not self.conn:
            return [], "Not connected"
        try:
            with self.conn.cursor(cursor_factory=RealDictCursor) as cur:
                cur.execute(query)
                return cur.fetchall(), None
        except Exception as e:
            err_msg = str(e)
            try:
                self.conn.close()
            except Exception:
                pass
            self.conn = None
            if self.connect():
                try:
                    with self.conn.cursor(cursor_factory=RealDictCursor) as cur:
                        cur.execute(query)
                        return cur.fetchall(), None
                except Exception as e2:
                    return [], str(e2)
            return [], err_msg
    
    def close(self):
        """Close database connection."""
        if self.conn:
            self.conn.close()


class DatabaseViewer:
    """Main curses-based database viewer application."""
    
    def __init__(self, stdscr):
        self.stdscr = stdscr
        self.current_tab = 0  # 0=users, 1=accounts, 2=transactions, 3=balance_history
        self.tabs = ['Users', 'Accounts', 'Transactions', 'Balance History']
        self.scroll_offset = 0
        self.db = None
        self.api_base = "http://localhost:8080"
        self.jwt_token: Optional[str] = None
        self.auth_email: Optional[str] = None
        self.jwt_user_id: Optional[str] = None
        self.account_numbers: List[str] = []
        self.auth_message: Optional[str] = None
        self.last_error = None
        self.needs_refresh = True  # Flag to track if screen needs redraw
        self.last_data_refresh = time.monotonic()  # Time of last data fetch (for periodic refresh)
        self.data_refresh_interval = 2  # Re-fetch data from DB every N seconds
        self.content_start_y = 6

        # Initialize curses
        curses.curs_set(0)  # Hide cursor
        curses.use_default_colors()
        self.stdscr.nodelay(1)  # Non-blocking input
        self.stdscr.timeout(1000)  # Check input every 1 second
        
        # Color pairs
        curses.start_color()
        curses.init_pair(1, curses.COLOR_CYAN, -1)  # Header
        curses.init_pair(2, curses.COLOR_GREEN, -1)  # Active tab
        curses.init_pair(3, curses.COLOR_YELLOW, -1)  # Inactive tab
        curses.init_pair(4, curses.COLOR_RED, -1)  # Error
        curses.init_pair(5, curses.COLOR_WHITE, curses.COLOR_BLUE)  # Selected row
    
    def load_config(self) -> bool:
        """Load database configuration."""
        try:
            config = ConfigParser.parse_config('src/main/resources/application.conf')
            db_config = config.get('database', {})
            if not db_config:
                self.last_error = "Database section not found in config"
                return False
            srv = config.get('server', {})
            host = srv.get('host', 'localhost').strip('"')
            port = srv.get('port', '8080')
            self.api_base = f"http://{host}:{port}"
            self.db = DatabaseConnection(db_config)
            if not self.db.connect():
                self.last_error = self.db.last_error or "Failed to connect to database"
                return False
            return True
        except FileNotFoundError:
            self.last_error = "Config file not found: src/main/resources/application.conf"
            return False
        except Exception as e:
            self.last_error = str(e)
            return False
    
    def format_value(self, value, column: Optional[str] = None) -> str:
        """Format a value for display."""
        if value is None:
            return "NULL"
        if column == "password_hash" and isinstance(value, str) and len(value) > 14:
            return value[:11] + "…"
        if isinstance(value, datetime):
            return value.strftime("%Y-%m-%d %H:%M:%S")
        if isinstance(value, (int, float)):
            return str(value)
        return str(value)
    
    def truncate_string(self, s: str, max_len: int) -> str:
        """Truncate string to max length."""
        if len(s) <= max_len:
            return s
        return s[:max_len-3] + '...'
    
    def safe_addstr(self, y: int, x: int, text: str, attr=0):
        """Safely add string to screen, handling boundaries."""
        try:
            height, width = self.stdscr.getmaxyx()
            if y < 0 or y >= height or x < 0 or x >= width:
                return
            text = text[:width - x] if x + len(text) > width else text
            if text:
                self.stdscr.addstr(y, x, text, attr)
        except curses.error:
            pass
    
    def draw_header(self):
        """Draw application header with tabs."""
        height, width = self.stdscr.getmaxyx()
        
        if height < 5 or width < 20:
            self.safe_addstr(0, 0, "Terminal too small!", curses.color_pair(4))
            return
        
        # Title
        title = "MTS Database Viewer"
        title_x = max(0, (width - len(title)) // 2)
        self.safe_addstr(0, title_x, title, curses.color_pair(1) | curses.A_BOLD)
        
        # Tabs
        tab_y = 2
        x = 2
        for i, tab_name in enumerate(self.tabs):
            if i == self.current_tab:
                attr = curses.color_pair(2) | curses.A_BOLD
            else:
                attr = curses.color_pair(3)
            
            tab_text = f" [{tab_name}] "
            if x + len(tab_text) < width:
                self.safe_addstr(tab_y, x, tab_text, attr)
                x += len(tab_text)
        
        # Instructions
        instructions = (
            "Q: Quit | ←→: Tabs | ↑↓: Scroll | R: Refresh | A: Login | T: Transfer | O: Logout"
        )
        self.safe_addstr(tab_y + 1, 2, instructions[:width-4], curses.color_pair(3))
        if self.jwt_token and self.auth_email:
            auth_line = f"API: {self.auth_email} (JWT ok)"
            self.safe_addstr(tab_y + 2, 2, auth_line[: width - 4], curses.color_pair(2))
            if self.account_numbers:
                acc_preview = ", ".join(self.account_numbers)
                acc_line = f"Your accounts ({len(self.account_numbers)}): {acc_preview}"
                self.safe_addstr(
                    tab_y + 3,
                    2,
                    self.truncate_string(acc_line, width - 4),
                    curses.color_pair(3),
                )
                err_y = tab_y + 4
                last_info_row = tab_y + 3
            else:
                err_y = tab_y + 3
                last_info_row = tab_y + 2
        else:
            err_y = tab_y + 2
            last_info_row = tab_y + 1

        # Error message
        if self.last_error:
            error_msg = f"Error: {self.last_error}"
            self.safe_addstr(err_y, 2, self.truncate_string(error_msg, width - 4), curses.color_pair(4))
            self.content_start_y = min(height - 3, max(5, err_y + 2))
        else:
            self.content_start_y = min(height - 3, max(5, last_info_row + 2))
    
    def draw_table(self, data: List[Dict], headers: List[str], col_widths: List[int], 
                   start_y: int, max_rows: int):
        """Draw a table with data."""
        height, width = self.stdscr.getmaxyx()
        
        if start_y >= height - 1:
            return
        
        # Draw header
        y = start_y
        x = 1
        for i, header in enumerate(headers):
            if x >= width - 1:
                break
            header_text = self.truncate_string(header, col_widths[i])
            self.safe_addstr(y, x, header_text.ljust(col_widths[i]), 
                             curses.color_pair(1) | curses.A_BOLD | curses.A_REVERSE)
            x += col_widths[i] + 1
        
        # Draw separator
        y += 1
        if y < height:
            sep_len = min(sum(col_widths) + len(col_widths) - 1, width - 2)
            self.safe_addstr(y, 1, '-' * sep_len, curses.color_pair(1))
        
        # Draw data rows
        y += 1
        visible_data = data[self.scroll_offset:self.scroll_offset + max_rows]
        
        for row_idx, row in enumerate(visible_data):
            if y >= height - 2:
                break
            
            x = 1
            for i, header in enumerate(headers):
                if x >= width - 1:
                    break
                value = row.get(header, "")
                value_str = self.format_value(value, column=header)
                value_str = self.truncate_string(value_str, col_widths[i])
                self.safe_addstr(y, x, value_str.ljust(col_widths[i]))
                x += col_widths[i] + 1
            
            y += 1
        
        # Draw scroll indicator
        if len(data) > max_rows and height > 1:
            scroll_info = f"Rows {self.scroll_offset + 1}-{min(self.scroll_offset + max_rows, len(data))} of {len(data)}"
            self.safe_addstr(height - 1, 2, scroll_info[:width-4], curses.color_pair(3))
    
    def get_users_data(self) -> Tuple[List[Dict], List[str], List[int], Optional[str]]:
        """Get users table data."""
        query = """
            SELECT id, user_name, email, password_hash, phone, created_at, updated_at, is_active
            FROM bank.users
            ORDER BY created_at DESC
        """
        data, err = self.db.execute_query(query) if self.db else ([], "No DB")
        headers = [
            "id",
            "user_name",
            "email",
            "password_hash",
            "phone",
            "created_at",
            "updated_at",
            "is_active",
        ]
        col_widths = [36, 16, 22, 18, 14, 18, 18, 6]
        return data, headers, col_widths, err
    
    def get_accounts_data(self) -> Tuple[List[Dict], List[str], List[int], Optional[str]]:
        """Get accounts table data."""
        query = """
            SELECT id, user_id, account_number, currency_code, balance, created_at, updated_at, is_active
            FROM bank.accounts
            ORDER BY created_at DESC
        """
        data, err = self.db.execute_query(query) if self.db else ([], "No DB")
        headers = ['id', 'user_id', 'account_number', 'currency_code', 'balance', 'created_at', 'updated_at', 'is_active']
        col_widths = [38, 38, 14, 5, 12, 20, 20, 8]
        return data, headers, col_widths, err

    def get_transactions_data(self) -> Tuple[List[Dict], List[str], List[int], Optional[str]]:
        """Get transactions table data. DDL: from_account, to_account reference account_number."""
        query = """
            SELECT id, from_account, to_account, amount, currency_code, exchange_rate, created_at
            FROM bank.transactions
            ORDER BY created_at DESC
            LIMIT 100
        """
        data, err = self.db.execute_query(query) if self.db else ([], "No DB")
        headers = ['id', 'from_account', 'to_account', 'amount', 'currency_code', 'exchange_rate', 'created_at']
        col_widths = [38, 14, 14, 12, 5, 12, 20]
        return data, headers, col_widths, err
    
    def get_balance_history_data(self) -> Tuple[List[Dict], List[str], List[int], Optional[str]]:
        """Get balance_history table data."""
        query = """
            SELECT id, account_number, old_balance, new_balance, amount, created_at
            FROM bank.balance_history
            ORDER BY created_at DESC
            LIMIT 100
        """
        data, err = self.db.execute_query(query) if self.db else ([], "No DB")
        headers = ['id', 'account_number', 'old_balance', 'new_balance', 'amount', 'created_at']
        col_widths = [38, 15, 12, 12, 12, 20]
        return data, headers, col_widths, err
    
    def draw_content(self):
        """Draw main content area."""
        height, width = self.stdscr.getmaxyx()
        start_y = getattr(self, "content_start_y", 6)
        max_rows = max(1, height - start_y - 3)
        
        if not self.db:
            error_msg = "Not connected to database. Check configuration."
            self.safe_addstr(start_y, 2, error_msg, curses.color_pair(4))
            return
        
        # Get data based on current tab (always fresh from DB)
        self.last_data_refresh = time.monotonic()
        try:
            if self.current_tab == 0:
                data, headers, col_widths, err = self.get_users_data()
            elif self.current_tab == 1:
                data, headers, col_widths, err = self.get_accounts_data()
            elif self.current_tab == 2:
                data, headers, col_widths, err = self.get_transactions_data()
            else:
                data, headers, col_widths, err = self.get_balance_history_data()
        except Exception as e:
            self.last_error = str(e)
            error_msg = f"Error loading data: {str(e)}"
            self.safe_addstr(start_y, 2, error_msg[:width-4], curses.color_pair(4))
            return
        if err:
            self.last_error = err
        else:
            self.last_error = None
        # Adjust column widths if needed
        total_width = sum(col_widths) + len(col_widths) - 1
        if total_width > width - 2:
            # Scale down proportionally
            scale = (width - 2) / total_width
            col_widths = [max(8, int(w * scale)) for w in col_widths]
        
        self.draw_table(data, headers, col_widths, start_y, max_rows)

    def run_auth_overlay(self) -> None:
        """Blocking login/register dialog (JWT via backend HTTP API)."""
        height, width = self.stdscr.getmaxyx()
        if height < 14 or width < 46:
            self.last_error = "Terminal too small for auth dialog"
            return

        self.stdscr.nodelay(0)
        self.stdscr.timeout(-1)
        curses.curs_set(1)

        mode = "login"
        email = ""
        password = ""
        user_name = ""
        phone = ""
        field_idx = 0
        status_line = ""

        def order():
            return ["email", "password"] if mode == "login" else ["email", "password", "user_name", "phone"]

        def get_field(name: str) -> str:
            return {"email": email, "password": password, "user_name": user_name, "phone": phone}[name]

        def set_field(name: str, val: str) -> None:
            nonlocal email, password, user_name, phone
            if name == "email":
                email = val
            elif name == "password":
                password = val
            elif name == "user_name":
                user_name = val
            else:
                phone = val

        def label(name: str) -> str:
            return {"email": "Email", "password": "Password", "user_name": "User name", "phone": "Phone"}.get(
                name, name
            )

        def draw() -> None:
            self.stdscr.erase()
            h, w = self.stdscr.getmaxyx()
            title = "Login (POST /auth/token)" if mode == "login" else "Register (POST /auth/register)"
            self.safe_addstr(0, 2, title[: w - 4], curses.color_pair(1) | curses.A_BOLD)
            self.safe_addstr(1, 2, f"API: {self.api_base}"[: w - 4], curses.color_pair(3))
            self.safe_addstr(2, 2, "Tab: switch mode | Enter: next field / submit | Esc: close", curses.color_pair(3))
            y = 4
            names = order()
            for i, name in enumerate(names):
                prefix = ">" if i == field_idx else " "
                self.safe_addstr(y, 2, f"{prefix} {label(name)}:", curses.color_pair(2 if i == field_idx else 3))
                y += 1
                raw = get_field(name)
                if name == "password":
                    shown = "*" * len(raw)
                else:
                    shown = raw
                fill = max(0, w - 6)
                self.safe_addstr(y, 4, (shown + " ").ljust(fill)[:fill])
                y += 1
            y += 1
            bad = (
                status_line.startswith("HTTP")
                or "Unauthorized" in status_line
                or "required" in status_line.lower()
                or "at least" in status_line.lower()
            )
            self.safe_addstr(
                y,
                2,
                status_line[: w - 4],
                curses.color_pair(4) if bad else curses.color_pair(2),
            )

        def edit_current() -> str:
            """esc | tab | enter"""
            names = order()
            fname = names[field_idx]
            buf = list(get_field(fname))
            pos = len(buf)
            max_len = 120 if fname != "password" else 128
            while True:
                names = order()
                fname = names[field_idx]
                h, w = self.stdscr.getmaxyx()
                row = 5 + field_idx * 2
                col = 4
                clear_w = max(1, w - col - 2)
                if fname == "password":
                    self.safe_addstr(row, col, " " * clear_w)
                    vis = ("*" * len(buf))[:clear_w]
                    self.safe_addstr(row, col, vis)
                else:
                    s = "".join(buf)
                    self.safe_addstr(row, col, " " * clear_w)
                    self.safe_addstr(row, col, s[:clear_w])
                cur_col = col + min(pos, max(0, len(buf)), max(0, clear_w - 1))
                self.stdscr.move(row, min(cur_col, w - 1))
                self.stdscr.refresh()
                ch = self.stdscr.getch()
                if ch == 27:  # Esc
                    return "esc"
                if ch == 9:  # Tab — switch login/register
                    set_field(fname, "".join(buf))
                    return "tab"
                if ch in (curses.KEY_ENTER, 10, 13):
                    set_field(fname, "".join(buf))
                    return "enter"
                if ch in (curses.KEY_BACKSPACE, 127, 8):
                    if pos > 0:
                        pos -= 1
                        buf.pop(pos)
                    continue
                if ch == curses.KEY_LEFT and pos > 0:
                    pos -= 1
                    continue
                if ch == curses.KEY_RIGHT and pos < len(buf):
                    pos += 1
                    continue
                if 32 <= ch < 127 and len(buf) < max_len:
                    buf.insert(pos, chr(ch))
                    pos += 1

        try:
            while True:
                if field_idx >= len(order()):
                    field_idx = 0
                draw()
                action = edit_current()
                if action == "esc":
                    break
                if action == "tab":
                    mode = "register" if mode == "login" else "login"
                    field_idx = 0
                    status_line = ""
                    continue
                names = order()
                if field_idx < len(names) - 1:
                    field_idx += 1
                    status_line = ""
                    continue
                em = email.strip()
                pw = password
                un = user_name.strip()
                ph = phone.strip()
                if mode == "login":
                    if not em or not pw:
                        status_line = "Email and password required"
                        field_idx = 0
                        continue
                    code, body = http_post_json(f"{self.api_base}/auth/token", {"email": em, "password": pw})
                    if code == 200:
                        try:
                            parsed = json.loads(body)
                            tok = parsed.get("token")
                            if tok:
                                self.jwt_token = tok
                                self.auth_email = em
                                acct = parsed.get("accountNumbers")
                                if isinstance(acct, list):
                                    self.account_numbers = [str(x) for x in acct if x is not None]
                                else:
                                    self.account_numbers = []
                                self.jwt_user_id = jwt_user_id(tok)
                                status_line = (
                                    f"Login OK — {len(self.account_numbers)} account(s), T: transfer"
                                )
                            else:
                                status_line = "Bad response: no token"
                        except json.JSONDecodeError:
                            status_line = "Invalid JSON from server"
                    else:
                        _, tw = self.stdscr.getmaxyx()
                        status_line = self.truncate_string(f"HTTP {code}: {body}", max(20, tw - 6))
                    field_idx = 0
                else:
                    if not em or not pw or not un:
                        status_line = "email, password (8+ chars), user name required"
                        field_idx = 0
                        continue
                    if len(pw) < 8:
                        status_line = "password must be at least 8 characters"
                        field_idx = 1
                        continue
                    code, body = http_post_json(
                        f"{self.api_base}/auth/register",
                        {"email": em, "password": pw, "userName": un, "phone": ph},
                    )
                    if code == 200:
                        try:
                            parsed = json.loads(body)
                            uid = parsed.get("userId", "?")
                            acc = parsed.get("accountNumber", "?")
                            status_line = f"Registered userId={uid} account={acc}"
                        except json.JSONDecodeError:
                            status_line = "Registered (non-JSON body)"
                    else:
                        _, tw = self.stdscr.getmaxyx()
                        status_line = self.truncate_string(f"HTTP {code}: {body}", max(20, tw - 6))
                    field_idx = 0
        finally:
            curses.curs_set(0)
            self.stdscr.nodelay(1)
            self.stdscr.timeout(1000)

    def run_transfer_overlay(self) -> None:
        """POST /transfer/account-number with Bearer JWT."""
        if not self.jwt_token:
            self.last_error = "Login first (A)"
            return
        if not self.account_numbers:
            self.last_error = "No accounts from server — relogin"
            return
        uid = self.jwt_user_id or jwt_user_id(self.jwt_token)
        if not uid:
            self.last_error = "Cannot read userId from JWT"
            return

        height, width = self.stdscr.getmaxyx()
        if height < 16 or width < 50:
            self.last_error = "Terminal too small for transfer dialog"
            return

        self.stdscr.nodelay(0)
        self.stdscr.timeout(-1)
        curses.curs_set(1)

        from_idx = 0
        to_account = ""
        amount_str = ""
        field_idx = 0  # 0=from, 1=to, 2=amount
        status_line = "Tab: field | From: ↑↓ | Esc: close"

        def draw_tf() -> None:
            self.stdscr.erase()
            h, w = self.stdscr.getmaxyx()
            self.safe_addstr(0, 2, "Transfer (POST /transfer/account-number)", curses.color_pair(1) | curses.A_BOLD)
            self.safe_addstr(1, 2, f"API: {self.api_base}"[: w - 4], curses.color_pair(3))
            y = 3
            prefix = ">" if field_idx == 0 else " "
            cur_from = self.account_numbers[from_idx]
            self.safe_addstr(
                y,
                2,
                f"{prefix} From (your account): {cur_from}",
                curses.color_pair(2 if field_idx == 0 else 3),
            )
            y += 2
            prefix = ">" if field_idx == 1 else " "
            self.safe_addstr(y, 2, f"{prefix} To account number:", curses.color_pair(2 if field_idx == 1 else 3))
            y += 1
            fill = max(0, w - 6)
            self.safe_addstr(y, 4, (to_account + " ").ljust(fill)[:fill])
            y += 2
            prefix = ">" if field_idx == 2 else " "
            self.safe_addstr(y, 2, f"{prefix} Amount:", curses.color_pair(2 if field_idx == 2 else 3))
            y += 1
            self.safe_addstr(y, 4, (amount_str + " ").ljust(fill)[:fill])
            y += 2
            bad = status_line.startswith("HTTP") or "error" in status_line.lower() or status_line.startswith("Invalid")
            self.safe_addstr(
                y,
                2,
                status_line[: w - 4],
                curses.color_pair(4) if bad else curses.color_pair(3),
            )

        def edit_line(initial: str, row: int, max_len: int = 32) -> Tuple[str, str]:
            """Returns (new_value, action) action in esc|tab|enter."""
            buf = list(initial)
            pos = len(buf)
            while True:
                h, w = self.stdscr.getmaxyx()
                col = 4
                clear_w = max(1, w - col - 2)
                s = "".join(buf)
                self.safe_addstr(row, col, " " * clear_w)
                self.safe_addstr(row, col, s[:clear_w])
                cur_col = col + min(pos, max(0, len(buf)), max(0, clear_w - 1))
                self.stdscr.move(row, min(cur_col, w - 1))
                self.stdscr.refresh()
                ch = self.stdscr.getch()
                if ch == 27:
                    return "".join(buf), "esc"
                if ch == 9:
                    return "".join(buf), "tab"
                if ch in (curses.KEY_ENTER, 10, 13):
                    return "".join(buf), "enter"
                if ch in (curses.KEY_BACKSPACE, 127, 8):
                    if pos > 0:
                        pos -= 1
                        buf.pop(pos)
                    continue
                if ch == curses.KEY_LEFT and pos > 0:
                    pos -= 1
                    continue
                if ch == curses.KEY_RIGHT and pos < len(buf):
                    pos += 1
                    continue
                if 32 <= ch < 127 and len(buf) < max_len:
                    buf.insert(pos, chr(ch))
                    pos += 1

        try:
            while True:
                draw_tf()
                if field_idx == 0:
                    ch = self.stdscr.getch()
                    if ch == 27:
                        break
                    if ch == 9:
                        field_idx = 1
                        status_line = "Tab: field | From: ↑↓ | Esc: close"
                        continue
                    if ch in (curses.KEY_ENTER, 10, 13):
                        field_idx = 1
                        status_line = "Tab: field | From: ↑↓ | Esc: close"
                        continue
                    if ch == curses.KEY_UP:
                        from_idx = (from_idx - 1) % len(self.account_numbers)
                        continue
                    if ch == curses.KEY_DOWN:
                        from_idx = (from_idx + 1) % len(self.account_numbers)
                        continue
                elif field_idx == 1:
                    row = 6
                    to_account, action = edit_line(to_account, row, max_len=24)
                    if action == "esc":
                        break
                    if action == "tab":
                        field_idx = 2
                        continue
                    field_idx = 2
                else:
                    row = 9
                    amount_str, action = edit_line(amount_str, row, max_len=20)
                    if action == "esc":
                        break
                    if action == "tab":
                        field_idx = 0
                        continue
                    frm = self.account_numbers[from_idx]
                    to = to_account.strip()
                    if not to:
                        status_line = "To account required"
                        field_idx = 1
                        continue
                    if to == frm:
                        status_line = "From and to must differ"
                        field_idx = 1
                        continue
                    try:
                        amt = float(amount_str.replace(",", ".").strip())
                    except ValueError:
                        status_line = "Invalid amount"
                        field_idx = 2
                        continue
                    if amt <= 0:
                        status_line = "Amount must be positive"
                        field_idx = 2
                        continue
                    payload = {
                        "userId": uid,
                        "fromAccount": frm,
                        "toAccount": to,
                        "amount": amt,
                    }
                    code, body = http_post_json(
                        f"{self.api_base}/transfer/account-number",
                        payload,
                        bearer=self.jwt_token,
                    )
                    if code == 200:
                        status_line = "Transfer OK"
                        to_account = ""
                        amount_str = ""
                        field_idx = 0
                    else:
                        tw = self.stdscr.getmaxyx()[1]
                        status_line = self.truncate_string(f"HTTP {code}: {body}", max(20, tw - 6))
                        field_idx = 2
        finally:
            curses.curs_set(0)
            self.stdscr.nodelay(1)
            self.stdscr.timeout(1000)

    def handle_input(self, key: int) -> bool:
        """Handle user input. Returns False if should quit."""
        if key == ord('q') or key == ord('Q'):
            return False
        
        if key == ord('r') or key == ord('R'):
            # Refresh data
            self.scroll_offset = 0
            self.needs_refresh = True
            return True

        if key in (ord("a"), ord("A")):
            self.run_auth_overlay()
            self.needs_refresh = True
            return True

        if key in (ord("o"), ord("O")):
            self.jwt_token = None
            self.auth_email = None
            self.jwt_user_id = None
            self.account_numbers = []
            self.needs_refresh = True
            return True

        if key in (ord("t"), ord("T")):
            self.run_transfer_overlay()
            self.needs_refresh = True
            return True

        if key == curses.KEY_LEFT:
            self.current_tab = (self.current_tab - 1) % len(self.tabs)
            self.scroll_offset = 0
            self.needs_refresh = True
            return True
        
        if key == curses.KEY_RIGHT:
            self.current_tab = (self.current_tab + 1) % len(self.tabs)
            self.scroll_offset = 0
            self.needs_refresh = True
            return True
        
        if key == curses.KEY_UP:
            if self.scroll_offset > 0:
                self.scroll_offset -= 1
                self.needs_refresh = True
            return True
        
        if key == curses.KEY_DOWN:
            self.scroll_offset += 1
            self.needs_refresh = True
            return True
        
        return True
    
    def run(self):
        """Main application loop."""
        try:
            if not self.load_config():
                error_msg = f"Failed to connect to database: {self.last_error or 'Unknown error'}"
                self.safe_addstr(0, 0, error_msg[:80])
                self.safe_addstr(1, 0, "Press any key to exit...")
                self.stdscr.nodelay(0)
                self.stdscr.timeout(-1)
                self.stdscr.getch()
                return
            
            # Initial draw
            self.stdscr.clear()
            self.draw_header()
            self.draw_content()
            self.stdscr.refresh()
            
            while True:
                try:
                    # Check for input (non-blocking with timeout)
                    key = self.stdscr.getch()
                    
                    # Only process if there's actual input or we need to refresh
                    if key != -1:
                        # Real input received
                        if not self.handle_input(key):
                            break
                        self.needs_refresh = True
                    else:
                        # Timeout: periodic data refresh so tables stay up to date
                        if time.monotonic() - self.last_data_refresh >= self.data_refresh_interval:
                            self.needs_refresh = True
                    
                    # Redraw only if needed
                    if self.needs_refresh:
                        self.stdscr.clear()
                        self.draw_header()
                        self.draw_content()
                        self.stdscr.refresh()
                        self.needs_refresh = False
                    
                except curses.error:
                    # Terminal resized or other curses error, force refresh
                    self.needs_refresh = True
                except Exception as e:
                    # Unexpected error, show it
                    self.last_error = str(e)
                    self.needs_refresh = True
        finally:
            if self.db:
                self.db.close()


def main():
    """Entry point."""
    def run_app(stdscr):
        viewer = DatabaseViewer(stdscr)
        viewer.run()
    
    try:
        curses.wrapper(run_app)
    except KeyboardInterrupt:
        pass
    except Exception as e:
        # If curses fails completely, print error to stderr
        import sys
        print(f"Fatal error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main()

