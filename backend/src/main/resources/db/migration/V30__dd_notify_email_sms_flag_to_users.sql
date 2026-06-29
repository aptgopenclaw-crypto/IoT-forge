-- Migration: Add notify_email_flag and notify_sms_flag columns to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS notify_email_flag boolean DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS notify_sms_flag boolean DEFAULT false;
