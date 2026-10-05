CREATE TABLE `identification_stages` (
	`observation_id` text NOT NULL,
	`user_id` text NOT NULL,
	`stage` text NOT NULL,
	`data` text NOT NULL,
	`created_at` text NOT NULL,
	PRIMARY KEY(`observation_id`, `stage`),
	FOREIGN KEY (`observation_id`) REFERENCES `observations`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `identification_stages_owner` ON `identification_stages` (`user_id`);