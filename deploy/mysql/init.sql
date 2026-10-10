CREATE DATABASE IF NOT EXISTS mall_user CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS mall_product CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS mall_order CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'mall'@'%' IDENTIFIED BY 'mall_lab_password';
GRANT ALL PRIVILEGES ON mall_user.* TO 'mall'@'%';
GRANT ALL PRIVILEGES ON mall_product.* TO 'mall'@'%';
GRANT ALL PRIVILEGES ON mall_order.* TO 'mall'@'%';
