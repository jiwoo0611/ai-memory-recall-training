// The server assigns a permanent sequence within each album. IDs remain routing keys only.
export function trainingName(session) {
  return session?.displayName?.trim() || session?.photo?.albumTitle?.trim() || '훈련';
}
