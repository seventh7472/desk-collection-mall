CREATE TABLE IF NOT EXISTS products (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    category VARCHAR(50) NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    stock INT NOT NULL,
    image VARCHAR(200) NOT NULL
);
CREATE TABLE IF NOT EXISTS reservations (
    reservation_key VARCHAR(128) PRIMARY KEY,
    request_fingerprint VARCHAR(2000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    total DECIMAL(12,2) NOT NULL
);
CREATE TABLE IF NOT EXISTS reservation_items (
    reservation_key VARCHAR(128) NOT NULL,
    product_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (reservation_key, product_id),
    FOREIGN KEY (reservation_key) REFERENCES reservations(reservation_key),
    FOREIGN KEY (product_id) REFERENCES products(id)
);
INSERT INTO products(id,name,description,category,price,stock,image)
SELECT 1,'机械键盘 K87','87 键紧凑布局，热插拔轴体，适合编程与日常办公。','桌面办公',299.00,40,'/images/keyboard.svg'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id=1);
INSERT INTO products(id,name,description,category,price,stock,image)
SELECT 2,'无线鼠标 M3','轻巧握感与静音按键，支持蓝牙及 2.4G 双模连接。','桌面办公',129.00,60,'/images/mouse.svg'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id=2);
INSERT INTO products(id,name,description,category,price,stock,image)
SELECT 3,'27 英寸显示器','2K 高清 IPS 屏幕，可调节支架，适合多窗口办公。','显示设备',1299.00,20,'/images/monitor.svg'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id=3);
INSERT INTO products(id,name,description,category,price,stock,image)
SELECT 4,'USB-C 扩展坞','支持 HDMI、USB 及读卡接口，让轻薄笔记本连接更多设备。','数码配件',199.00,50,'/images/hub.svg'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id=4);
INSERT INTO products(id,name,description,category,price,stock,image)
SELECT 5,'降噪耳机 H5','无线连接与主动降噪，适合会议、学习及通勤。','音频设备',459.00,30,'/images/headset.svg'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id=5);
INSERT INTO products(id,name,description,category,price,stock,image)
SELECT 6,'便携固态硬盘 1TB','高速 USB-C 接口，铝合金机身，便于文档备份与随身携带。','数码配件',599.00,35,'/images/ssd.svg'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id=6);
