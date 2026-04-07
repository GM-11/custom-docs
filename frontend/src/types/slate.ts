// Slate requires module augmentation to type the editor's element and text
// nodes. Without this, all Slate generics fall back to `unknown`.
// See: https://docs.slatejs.org/concepts/12-typescript

export type CustomElement = {
  type: "paragraph";
  align?: "left" | "center" | "right";
  children: CustomText[];
};

export type CustomText = {
  text: string;
};

declare module "slate" {
  interface CustomTypes {
    Element: CustomElement;
    Text: CustomText;
  }
}
