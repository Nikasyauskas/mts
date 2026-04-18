export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  userName: string;
  phone?: string;
}

export interface AuthResponse {
  token: string;
  tokenType?: string;
  accountNumbers: string[];
}

export interface RegisterResponse {
  userId: string;
  accountNumber: string;
}

export interface TransferRequest {
  userId: string;
  fromAccount: string;
  toAccount: string;
  amount: number;
}
