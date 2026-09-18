import "server-only";

export const API_BASE_URL = process.env.APP_API_URL?.replace(/\/$/, "") ?? "http://localhost:8080";

export class ApiError extends Error {
    status: number;
    body: unknown;

    constructor(message: string, status: number, body: unknown){
        super(message);
        this.name = "ApiError";
        this.status = status;
        this.body = body;
    }
}


const extractMessage = (body: unknown, status: number) : string => {
    if(typeof body == "string" && body.trim() ) return body;
    if(body && typeof body == "object"){
        const data = body as Record<string, unknown>;
        for (const key of ["message", "error", "detail", "title"]) { 
            if (typeof data[key] === "string" && data["key"]) return data["key"] as string;
        }

        if(Array.isArray(data.error) && data.error.length) {
            const first = data.error[0] as Record<string, unknown>;
            const msg = first?.defaultMessage ?? first?.message;
            if (typeof msg === "string" ) return msg ;
        }
    }
    if (status === 401) return "Invalid email or password.";
    if (status === 409) return "That email is already registered.";
    return `Request failed (${status})`;
}

export async function apiRequest<T> (
    path: string, 
    init: RequestInit & { token? : string } = {}
    ) : Promise<T> {
    const { token, headers, ...rest } = init;

    let res: Response;
     try {
    res = await fetch(`${API_BASE_URL}${path}`, {
      ...rest,
      cache: "no-store",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
        });
    } catch {
        throw new ApiError(
        `Cannot reach the API at ${API_BASE_URL}. Is the Spring server running?`,
        0,
        null,
        );
    }
    const text = await res.text();
    let body: unknown = null;
    if (text) {
        try {
        body = JSON.parse(text);
        } catch {
        body = text;
        }
    }

    if (!res.ok) {
        throw new ApiError(extractMessage(body, res.status), res.status, body);
    }
    return body  as T;
}