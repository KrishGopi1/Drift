import { defineCollection } from "astro:content";
import { glob } from "astro/loaders";
import { z } from "astro/zod";
import { GUIDE_PATTERNS, slugFor } from "./lib/slug.mjs";

// The guide renders the repo's own markdown in place (site/ is one level down).
const guide = defineCollection({
  loader: glob({ base: "..", pattern: GUIDE_PATTERNS, generateId: ({ entry }) => slugFor(entry) }),
  schema: z.looseObject({}),
});

export const collections = { guide };
