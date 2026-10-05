CREATE TABLE `admin_audit` (
	`id` text PRIMARY KEY NOT NULL,
	`actor_id` text,
	`target_id` text,
	`action` text NOT NULL,
	`created_at` text NOT NULL
);
--> statement-breakpoint
CREATE TABLE `ai_usage` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`observation_id` text,
	`period` text NOT NULL,
	`purpose` text NOT NULL,
	`model` text NOT NULL,
	`input_tokens` integer NOT NULL,
	`output_tokens` integer NOT NULL,
	`micro_usd` integer NOT NULL,
	`created_at` text NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `ai_usage_user_period` ON `ai_usage` (`user_id`,`period`);--> statement-breakpoint
CREATE TABLE `avatar_generations` (
	`user_id` text NOT NULL,
	`period` text NOT NULL,
	`used` integer DEFAULT 0 NOT NULL,
	`locked_until` integer DEFAULT 0 NOT NULL,
	`token` text,
	PRIMARY KEY(`user_id`, `period`),
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE TABLE `member_access` (
	`user_id` text PRIMARY KEY NOT NULL,
	`role` text DEFAULT 'member' NOT NULL,
	`status` text DEFAULT 'active' NOT NULL,
	`updated_at` text NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE TABLE `photo_allowance` (
	`observation_id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`period` text NOT NULL,
	`bonus_year` text,
	`created_at` text NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `photo_allowance_owner_period` ON `photo_allowance` (`user_id`,`period`);--> statement-breakpoint
CREATE TABLE `publication_checks` (
	`observation_id` text PRIMARY KEY NOT NULL,
	`content_hash` text NOT NULL,
	`decision` text NOT NULL,
	`checked_at` text NOT NULL,
	FOREIGN KEY (`observation_id`) REFERENCES `observations`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE TABLE `subscriptions` (
	`token_hash` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`token_envelope` text NOT NULL,
	`product_id` text NOT NULL,
	`plan` text NOT NULL,
	`status` text NOT NULL,
	`starts_at` text NOT NULL,
	`expires_at` text NOT NULL,
	`checked_at` integer NOT NULL,
	`acknowledged` integer DEFAULT 0 NOT NULL,
	`is_trial` integer DEFAULT 0 NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `subscriptions_user_expiry` ON `subscriptions` (`user_id`,`expires_at`);