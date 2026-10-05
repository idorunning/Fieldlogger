CREATE TABLE `ai_reservations` (
	`id` text PRIMARY KEY NOT NULL,
	`day_key` text NOT NULL,
	`reserved_micro_usd` integer NOT NULL,
	`settled_micro_usd` integer,
	`created_at` text NOT NULL,
	`settled_at` text
);
