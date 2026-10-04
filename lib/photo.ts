import exifr from "exifr";
import { validCoords } from "./types";
export async function preparePhoto(file: File) {
  if (file.size > 35 * 1024 * 1024)
    throw Error("Please choose a photo smaller than 35 MB.");
  if (!/^image\/(jpeg|png|webp|heic|heif)/.test(file.type))
    throw Error("Choose a JPEG, PNG or WebP photo.");
  let metadata: any = {};
  try {
    metadata =
      (await exifr.parse(file, {
        gps: true,
        pick: [
          "DateTimeOriginal",
          "latitude",
          "longitude",
          "OffsetTimeOriginal",
        ],
      })) || {};
  } catch {}
  const bitmap = await createImageBitmap(file).catch(() => {
    throw Error(
      "This photo format cannot be opened here. Please use JPEG or PNG.",
    );
  });
  const scale = Math.min(1, 1800 / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement("canvas");
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  const ctx = canvas.getContext("2d");
  if (!ctx) throw Error("Could not prepare the photo.");
  ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();
  const photo = await new Promise<Blob>((resolve, reject) =>
    canvas.toBlob(
      (blob) =>
        blob ? resolve(blob) : reject(Error("Could not save the photo.")),
      "image/jpeg",
      0.88,
    ),
  );
  return {
    photo,
    exifDate:
      metadata.DateTimeOriginal instanceof Date &&
      !isNaN(metadata.DateTimeOriginal.valueOf())
        ? metadata.DateTimeOriginal
        : null,
    gps: validCoords(metadata.latitude, metadata.longitude)
      ? {
          latitude: metadata.latitude as number,
          longitude: metadata.longitude as number,
        }
      : null,
  };
}
export async function locate(): Promise<GeolocationPosition> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation)
      return reject(Error("Location is unavailable in this browser."));
    navigator.geolocation.getCurrentPosition(resolve, reject, {
      enableHighAccuracy: true,
      timeout: 15000,
      maximumAge: 0,
    });
  });
}
export function localDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
