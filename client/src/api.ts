import axios from "axios";
import type { ChatRequestPayload, ChatResponse } from "./types";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE ?? "/api",
  timeout: 1000 * 30,
});

export async function apiChat(
  payload: ChatRequestPayload,
): Promise<ChatResponse> {
  const { data } = await api.post<ChatResponse>("/chat", payload);
  return data;
}
