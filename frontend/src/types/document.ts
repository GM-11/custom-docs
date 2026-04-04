export type Document = {
  id: string;      // UUID, serialized as string by Jackson
  ownerId: string;
  title: string;
  createdAt: string; // ISO 8601 — write-dates-as-timestamps=false is set in application.yaml
  updatedAt: string;
};
