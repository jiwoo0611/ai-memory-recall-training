export const MAX_PHOTO_BYTES = 30 * 1024 * 1024;
export const MAX_UPLOAD_BYTES = 100 * 1024 * 1024;
export const UPLOAD_HINT = 'JPG·PNG · 사진 한 장 최대 30MB · 한 번에 총 100MB 미만';

export function validateUploadSizes(files) {
  const tooLarge = files.find(file => file.size > MAX_PHOTO_BYTES);
  if (tooLarge) return `${tooLarge.name}: 사진 한 장은 최대 30MB까지 업로드할 수 있습니다.`;
  if (files.reduce((sum, file) => sum + file.size, 0) >= MAX_UPLOAD_BYTES)
    return '선택한 사진의 총용량이 100MB 이상입니다. 사진을 나누어 업로드해주세요.';
  return null;
}
