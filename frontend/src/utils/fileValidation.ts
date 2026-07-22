export const ACCEPTED_EXTENSIONS = [".java", ".zip"] as const;
export const MAX_SINGLE_FILE_MB = 2;
export const MAX_ZIP_MB = 20;

export interface ValidationError {
  message: string;
}

export function validateFile(file: File): ValidationError | null {
  const name = file.name.toLowerCase();
  const hasValidExtension = ACCEPTED_EXTENSIONS.some((ext) => name.endsWith(ext));
  if (!hasValidExtension) {
    return {
      message: `Unsupported file type. Accepted: ${ACCEPTED_EXTENSIONS.join(", ")}.`,
    };
  }

  if (file.size === 0) {
    return { message: "File is empty." };
  }

  const isZip = name.endsWith(".zip");
  const maxMb = isZip ? MAX_ZIP_MB : MAX_SINGLE_FILE_MB;
  const sizeMb = file.size / (1024 * 1024);
  if (sizeMb > maxMb) {
    return {
      message: `File is ${sizeMb.toFixed(1)} MB, max allowed is ${maxMb} MB.`,
    };
  }

  return null;
}
