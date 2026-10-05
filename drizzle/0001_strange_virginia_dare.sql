CREATE TABLE `account_keys` (
	`user_id` text PRIMARY KEY NOT NULL,
	`envelope` text NOT NULL,
	`updated_at` text NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
