CREATE TABLE `achievement_unlocks` (
	`user_id` text NOT NULL,
	`badge` text NOT NULL,
	`earned_at` text NOT NULL,
	PRIMARY KEY(`user_id`, `badge`),
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
ALTER TABLE `profiles` ADD `avatar` text DEFAULT '{}' NOT NULL;