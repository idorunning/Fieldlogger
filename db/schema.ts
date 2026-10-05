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
  avatar: text('avatar').notNull().default('{}'),
});
export const achievements = sqliteTable('achievement_unlocks', {
  userId: text('user_id').notNull().references(() => users.id,{onDelete:'cascade'}),
  badge: text('badge').notNull(),
  earnedAt: text('earned_at').notNull(),
}, t=>[primaryKey({columns:[t.userId,t.badge]})]);
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

export const memberAccess = sqliteTable('member_access', {
  userId: text('user_id').primaryKey().references(() => users.id,{onDelete:'cascade'}),
  role: text('role').notNull().default('member'),
  status: text('status').notNull().default('active'),
  updatedAt: text('updated_at').notNull(),
});
export const adminAudit = sqliteTable('admin_audit', {
  id: text('id').primaryKey(),
  actorId: text('actor_id'),
  targetId: text('target_id'),
  action: text('action').notNull(),
  createdAt: text('created_at').notNull(),
});
export const subscriptions = sqliteTable('subscriptions', {
  tokenHash: text('token_hash').primaryKey(),
  userId: text('user_id').notNull().references(() => users.id,{onDelete:'cascade'}),
  tokenEnvelope: text('token_envelope').notNull(),
  productId: text('product_id').notNull(),
  plan: text('plan').notNull(),
  status: text('status').notNull(),
  startsAt: text('starts_at').notNull(),
  expiresAt: text('expires_at').notNull(),
  checkedAt: integer('checked_at').notNull(),
  acknowledged: integer('acknowledged').notNull().default(0),
  isTrial: integer('is_trial').notNull().default(0),
  paidPeriodStart: text('paid_period_start'),
  paidPeriodEnd: text('paid_period_end'),
  latestOrderId: text('latest_order_id'),
},t=>[index('subscriptions_user_expiry').on(t.userId,t.expiresAt)]);
// Not deleted when a photo is archived/removed: those actions cannot reset quota.
export const photoAllowance = sqliteTable('photo_allowance', {
  observationId: text('observation_id').primaryKey(),
  userId: text('user_id').notNull().references(() => users.id,{onDelete:'cascade'}),
  period: text('period').notNull(),
  bonusYear: text('bonus_year'),
  createdAt: text('created_at').notNull(),
},t=>[index('photo_allowance_owner_period').on(t.userId,t.period)]);
export const avatarGenerations = sqliteTable('avatar_generations', {
  userId: text('user_id').notNull().references(() => users.id,{onDelete:'cascade'}),
  period: text('period').notNull(),
  used: integer('used').notNull().default(0),
  lockedUntil: integer('locked_until').notNull().default(0),
  token: text('token'),
},t=>[primaryKey({columns:[t.userId,t.period]})]);
export const aiUsage = sqliteTable('ai_usage', {
  id: text('id').primaryKey(),
  userId: text('user_id').notNull().references(() => users.id,{onDelete:'cascade'}),
  observationId: text('observation_id'),
  period: text('period').notNull(),
  purpose: text('purpose').notNull(),
  model: text('model').notNull(),
  inputTokens: integer('input_tokens').notNull(),
  outputTokens: integer('output_tokens').notNull(),
  microUsd: integer('micro_usd').notNull(),
  createdAt: text('created_at').notNull(),
},t=>[index('ai_usage_user_period').on(t.userId,t.period)]);
// Anonymous provider-cost accounting survives account deletion while a request
// is in flight. No account, photograph or credential fields belong here.
export const aiReservations = sqliteTable('ai_reservations', {
  id: text('id').primaryKey(),
  dayKey: text('day_key').notNull(),
  reservedMicroUsd: integer('reserved_micro_usd').notNull(),
  settledMicroUsd: integer('settled_micro_usd'),
  createdAt: text('created_at').notNull(),
  settledAt: text('settled_at'),
});
export const publicationChecks = sqliteTable('publication_checks', {
  observationId: text('observation_id').primaryKey().references(() => observations.id,{onDelete:'cascade'}),
  contentHash: text('content_hash').notNull(),
  decision: text('decision').notNull(),
  checkedAt: text('checked_at').notNull(),
});
export const serviceConfig = sqliteTable('service_config', {
  key: text('key').primaryKey(),
  envelope: text('envelope').notNull(),
  updatedAt: text('updated_at').notNull(),
});
// Completed model stages survive an interrupted final save, preventing paid
// first-pass or closer-review calls from being repeated for immutable photos.
export const identificationStages = sqliteTable('identification_stages', {
  observationId: text('observation_id').notNull().references(() => observations.id,{onDelete:'cascade'}),
  userId: text('user_id').notNull().references(() => users.id,{onDelete:'cascade'}),
  stage: text('stage').notNull(),
  data: text('data').notNull(),
  createdAt: text('created_at').notNull(),
},t=>[primaryKey({columns:[t.observationId,t.stage]}),index('identification_stages_owner').on(t.userId)]);
