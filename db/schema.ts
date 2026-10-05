import { sqliteTable, text, integer, real, index, primaryKey } from "drizzle-orm/sqlite-core";
export const users = sqliteTable("users", {
  id: text("id").primaryKey(),
  email: text("email").notNull().unique(),
  name: text("name").notNull(),
  passwordHash: text("password_hash").notNull(),
  salt: text("salt").notNull(),
  createdAt: text("created_at").notNull(),
});
export const sessions = sqliteTable("sessions", {
  hash: text("hash").primaryKey(),
  userId: text("user_id")
    .notNull()
    .references(() => users.id, { onDelete: "cascade" }),
  expiresAt: integer("expires_at").notNull(),
});
export const observations = sqliteTable(
  "observations",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    data: text("data").notNull(),
    photoKey: text("photo_key").notNull(),
    updatedAt: text("updated_at").notNull(),
  },
  (t) => [index("observations_user_updated").on(t.userId, t.updatedAt)],
);
export const authAttempts = sqliteTable("auth_attempts", {
  key: text("key").primaryKey(),
  count: integer("count").notNull(),
  resetAt: integer("reset_at").notNull(),
});
export const accountKeys = sqliteTable("account_keys", {
  userId: text("user_id")
    .primaryKey()
    .references(() => users.id, { onDelete: "cascade" }),
  envelope: text("envelope").notNull(),
  updatedAt: text("updated_at").notNull(),
});

// Community data stays separate from the private journal and account credentials.
export const profiles = sqliteTable('profiles', {
  userId: text('user_id').primaryKey().references(() => users.id, {onDelete:'cascade'}),
  username: text('username').notNull().unique(),
  discoverable: integer('discoverable').notNull().default(0),
  termsAt: text('terms_at'),
});
export const publications = sqliteTable('publications', {
  observationId: text('observation_id').primaryKey().references(() => observations.id, {onDelete:'cascade'}),
  ownerId: text('owner_id').notNull().references(() => users.id, {onDelete:'cascade'}),
  status: text('status').notNull(),
  audience: text('audience').notNull(),
  snapshot: text('snapshot'),
  photoKey: text('photo_key'),
  generation: text('generation').notNull(),
  centreLat: real('centre_lat'),
  centreLon: real('centre_lon'),
  radiusKm: real('radius_km'),
  reason: text('reason').notNull().default(''),
  createdAt: text('created_at').notNull(),
  updatedAt: text('updated_at').notNull(),
}, t => [index('publications_owner_status').on(t.ownerId,t.status)]);
export const recipients = sqliteTable('publication_recipients', {
  id: text('id').primaryKey(),
  observationId: text('observation_id').notNull().references(() => publications.observationId,{onDelete:'cascade'}),
  generation: text('generation').notNull(),
  email: text('email').notNull(),
  tokenHash: text('token_hash').notNull().unique(),
  acceptedUserId: text('accepted_user_id').references(() => users.id,{onDelete:'cascade'}),
});
export const acorns = sqliteTable('acorns', {
  observationId: text('observation_id').notNull().references(() => observations.id,{onDelete:'cascade'}),
  userId: text('user_id').notNull().references(() => users.id,{onDelete:'cascade'}),
  createdAt: text('created_at').notNull(),
},t => [primaryKey({columns:[t.observationId,t.userId]})]);
export const savedPlaces = sqliteTable('saved_places', {
  observationId: text('observation_id').notNull().references(() => observations.id,{onDelete:'cascade'}),
  userId: text('user_id').notNull().references(() => users.id,{onDelete:'cascade'}),
  createdAt: text('created_at').notNull(),
},t => [primaryKey({columns:[t.observationId,t.userId]})]);
export const follows = sqliteTable('follows', {
  followerId: text('follower_id').notNull().references(() => users.id,{onDelete:'cascade'}),
  followingId: text('following_id').notNull().references(() => users.id,{onDelete:'cascade'}),
},t => [primaryKey({columns:[t.followerId,t.followingId]})]);
export const blocks = sqliteTable('blocks', {
  blockerId: text('blocker_id').notNull().references(() => users.id,{onDelete:'cascade'}),
  blockedId: text('blocked_id').notNull().references(() => users.id,{onDelete:'cascade'}),
},t => [primaryKey({columns:[t.blockerId,t.blockedId]})]);
export const reports = sqliteTable('reports', {
  id: text('id').primaryKey(),
  observationId: text('observation_id').notNull().references(() => publications.observationId,{onDelete:'cascade'}),
  reporterId: text('reporter_id').notNull().references(() => users.id,{onDelete:'cascade'}),
  reason: text('reason').notNull(),
  detail: text('detail').notNull().default(''),
  status: text('status').notNull().default('open'),
  createdAt: text('created_at').notNull(),
});
