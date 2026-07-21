import { describe, it, expect } from "vitest";
import { validateFile, MAX_SINGLE_FILE_MB, MAX_ZIP_MB } from "./fileValidation";

function makeFile(name: string, sizeBytes: number): File {
  const buffer = new ArrayBuffer(sizeBytes);
  return new File([buffer], name, { type: "application/octet-stream" });
}

describe("validateFile", () => {
  it("accepts a .java file under 2 MB", () => {
    const file = makeFile("Main.java", 1024);
    expect(validateFile(file)).toBeNull();
  });

  it("accepts a .zip file under 20 MB", () => {
    const file = makeFile("project.zip", 1024 * 1024);
    expect(validateFile(file)).toBeNull();
  });

  it("rejects a .java file over 2 MB", () => {
    const size = (MAX_SINGLE_FILE_MB + 1) * 1024 * 1024;
    const file = makeFile("Main.java", size);
    const result = validateFile(file);
    expect(result).not.toBeNull();
    expect(result!.message).toContain("max allowed is 2 MB");
  });

  it("rejects a .zip file over 20 MB", () => {
    const size = (MAX_ZIP_MB + 1) * 1024 * 1024;
    const file = makeFile("project.zip", size);
    const result = validateFile(file);
    expect(result).not.toBeNull();
    expect(result!.message).toContain("max allowed is 20 MB");
  });

  it("rejects an unsupported file type (.py)", () => {
    const file = makeFile("script.py", 100);
    const result = validateFile(file);
    expect(result).not.toBeNull();
    expect(result!.message).toContain("Unsupported file type");
  });

  it("accepts a file with double extension ending in .java", () => {
    const file = makeFile("Main.java.java", 100);
    expect(validateFile(file)).toBeNull();
  });

  it("accepts uppercase .ZIP extension", () => {
    const file = makeFile("PROJECT.ZIP", 1024);
    expect(validateFile(file)).toBeNull();
  });

  it("rejects an empty file (0 bytes)", () => {
    const file = makeFile("empty.java", 0);
    const result = validateFile(file);
    expect(result).not.toBeNull();
    expect(result!.message).toBe("File is empty.");
  });

  it("accepts a .java file exactly at 2 MB boundary", () => {
    const file = makeFile("Boundary.java", MAX_SINGLE_FILE_MB * 1024 * 1024);
    expect(validateFile(file)).toBeNull();
  });

  it("rejects a .txt file", () => {
    const file = makeFile("notes.txt", 100);
    const result = validateFile(file);
    expect(result).not.toBeNull();
    expect(result!.message).toContain("Unsupported file type");
  });
});
