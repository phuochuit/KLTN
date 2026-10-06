SELECT COUNT(*) AS household_count FROM households;
SELECT COUNT(*) AS vehicle_count FROM vehicles;
SELECT COUNT(*) AS session_count FROM parking_sessions;
SELECT COUNT(*) AS payment_count FROM subscription_payments;
SELECT id, household_code, apartment_number FROM households LIMIT 5;
SELECT id, plate_number, owner_name FROM vehicles LIMIT 5;
