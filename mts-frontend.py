#!/usr/bin/env python3
"""
Terminal application for displaying PostgreSQL database state.
Uses curses for terminal UI.
"""

import curses
import re
import sys
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
            self.last_error = None
            return True
        except Exception as e:
            self.last_error = str(e)
            return False
    
    def execute_query(self, query: str) -> List[Dict]:
        """Execute query and return results as list of dictionaries."""
        if not self.conn:
            return []
        
        try:
            with self.conn.cursor(cursor_factory=RealDictCursor) as cur:
                cur.execute(query)
                return cur.fetchall()
        except Exception:
            return []
    
    def close(self):
        """Close database connection."""
        if self.conn:
            self.conn.close()


class DatabaseViewer:
    """Main curses-based database viewer application."""
    
    def __init__(self, stdscr):
        self.stdscr = stdscr
        self.current_tab = 0  # 0=users, 1=transactions, 2=balance_history
        self.tabs = ['Users', 'Transactions', 'Balance History']
        self.scroll_offset = 0
        self.db = None
        self.last_error = None
        self.needs_refresh = True  # Flag to track if screen needs redraw
        
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
    
    def format_value(self, value) -> str:
        """Format a value for display."""
        if value is None:
            return 'NULL'
        if isinstance(value, datetime):
            return value.strftime('%Y-%m-%d %H:%M:%S')
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
        instructions = "Q: Quit | ←→: Switch tabs | ↑↓: Scroll | R: Refresh"
        self.safe_addstr(tab_y + 1, 2, instructions[:width-4], curses.color_pair(3))
        
        # Error message
        if self.last_error:
            error_msg = f"Error: {self.last_error}"
            self.safe_addstr(tab_y + 2, 2, self.truncate_string(error_msg, width - 4), 
                             curses.color_pair(4))
    
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
                value = row.get(header, '')
                value_str = self.format_value(value)
                value_str = self.truncate_string(value_str, col_widths[i])
                self.safe_addstr(y, x, value_str.ljust(col_widths[i]))
                x += col_widths[i] + 1
            
            y += 1
        
        # Draw scroll indicator
        if len(data) > max_rows and height > 1:
            scroll_info = f"Rows {self.scroll_offset + 1}-{min(self.scroll_offset + max_rows, len(data))} of {len(data)}"
            self.safe_addstr(height - 1, 2, scroll_info[:width-4], curses.color_pair(3))
    
    def get_users_data(self) -> Tuple[List[Dict], List[str], List[int]]:
        """Get users table data."""
        query = """
            SELECT id, account_number, balance, created_at, updated_at, is_active
            FROM bank.users
            ORDER BY created_at DESC
        """
        data = self.db.execute_query(query) if self.db else []
        
        headers = ['id', 'account_number', 'balance', 'created_at', 'updated_at', 'is_active']
        col_widths = [38, 15, 12, 20, 20, 8]
        
        return data, headers, col_widths
    
    def get_transactions_data(self) -> Tuple[List[Dict], List[str], List[int]]:
        """Get transactions table data."""
        query = """
            SELECT id, from_account_id, to_account_id, amount, created_at
            FROM bank.transactions
            ORDER BY created_at DESC
            LIMIT 100
        """
        data = self.db.execute_query(query) if self.db else []
        
        headers = ['id', 'from_account_id', 'to_account_id', 'amount', 'created_at']
        col_widths = [38, 15, 15, 12, 20]
        
        return data, headers, col_widths
    
    def get_balance_history_data(self) -> Tuple[List[Dict], List[str], List[int]]:
        """Get balance_history table data."""
        query = """
            SELECT id, account_number, old_balance, new_balance, amount, created_at
            FROM bank.balance_history
            ORDER BY created_at DESC
            LIMIT 100
        """
        data = self.db.execute_query(query) if self.db else []
        
        headers = ['id', 'account_number', 'old_balance', 'new_balance', 'amount', 'created_at']
        col_widths = [38, 15, 12, 12, 12, 20]
        
        return data, headers, col_widths
    
    def draw_content(self):
        """Draw main content area."""
        height, width = self.stdscr.getmaxyx()
        start_y = 6
        max_rows = max(1, height - start_y - 3)
        
        if not self.db:
            error_msg = "Not connected to database. Check configuration."
            self.safe_addstr(start_y, 2, error_msg, curses.color_pair(4))
            return
        
        # Get data based on current tab
        try:
            if self.current_tab == 0:
                data, headers, col_widths = self.get_users_data()
            elif self.current_tab == 1:
                data, headers, col_widths = self.get_transactions_data()
            else:
                data, headers, col_widths = self.get_balance_history_data()
        except Exception as e:
            error_msg = f"Error loading data: {str(e)}"
            self.safe_addstr(start_y, 2, error_msg[:width-4], curses.color_pair(4))
            return
        
        # Adjust column widths if needed
        total_width = sum(col_widths) + len(col_widths) - 1
        if total_width > width - 2:
            # Scale down proportionally
            scale = (width - 2) / total_width
            col_widths = [max(8, int(w * scale)) for w in col_widths]
        
        self.draw_table(data, headers, col_widths, start_y, max_rows)
    
    def handle_input(self, key: int) -> bool:
        """Handle user input. Returns False if should quit."""
        if key == ord('q') or key == ord('Q'):
            return False
        
        if key == ord('r') or key == ord('R'):
            # Refresh data
            self.scroll_offset = 0
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

