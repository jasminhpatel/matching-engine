drop table ADDRESS_STATE;
drop table ADDRESS_STATE_LOG;

create table ADDRESS_STATE (
id BIGINT(15) NOT NULL AUTO_INCREMENT,
sequence_number BIGINT(15),
insert_time VARCHAR(32) DEFAULT NULL,
address VARCHAR(64),
updateType INT(1),
userId BIGINT(16),
assetId INT(8),
symbol VARCHAR(16),
balance BIGINT(16),
balance_scale INT(4),
confirms INT(8),
source VARCHAR(256),
active INT(1),
updateBy VARCHAR(64),
signature VARCHAR(256),
status INT(4),
PRIMARY KEY (id)
);
CREATE INDEX `ADDRESS_STATE1` ON `ADDRESS_STATE` (`userId`);
CREATE INDEX `ADDRESS_STATE2` ON `ADDRESS_STATE` (`assetId`);
CREATE INDEX `ADDRESS_STATE3` ON `ADDRESS_STATE` (`active`);






drop table TRADE_HISTORY_LOG;


drop table BALANCE_LOG;
drop table BALANCE_STATE;
drop table FEE_LOG;
drop table MESSAGE_LOG;
drop table ORDER_BOOK_STATE;
drop table SECURITY_DEFINITION_LOG;
drop table USER_LOG;
drop table USER_STATE;
drop table WITHDRAW_REQUEST;
drop table EMAIL_LOG;
drop table FUND;
drop table GEO_IP;
drop table ADDRESSES;
drop table FUND_BALANCE_LOG;


create table EMAIL_LOG (
  	id BIGINT(15) NOT NULL AUTO_INCREMENT,
  	email VARCHAR(128) DEFAULT NULL,
  	insert_time VARCHAR(32) DEFAULT NULL,  
  	ip VARCHAR(128) DEFAULT NULL,
  	PRIMARY KEY (id)
);
  	
create table MESSAGE_LOG (
  	id BIGINT(15) NOT NULL AUTO_INCREMENT,
  	sequence_number BIGINT(15),
  	insert_time VARCHAR(32) DEFAULT NULL,
  	messageType CHAR(3),
  	orderId BIGINT(15),
  	execId BIGINT(15),
  	clOrdId VARCHAR(32) DEFAULT NULL,
  	securityId INT(8),
  	symbol VARCHAR(32),
  	side CHAR(3),
  	ordType CHAR(3),
  	execType CHAR(3),
  	execRestatementReason CHAR(3),
  	ordStatus CHAR(3),
  	account INT(12),
  	timeInForce CHAR(1),
  	expireTime VARCHAR(32) DEFAULT NULL,
  	timestamp VARCHAR(32) DEFAULT NULL,
  	orderQty BIGINT(15),
  	orderQty_scale INT(2),
  	leavesQty BIGINT(15),
  	leavesQty_scale INT(2),
  	cumQty BIGINT(15),
  	cumQty_scale INT(2),
  	price BIGINT(15),
  	price_scale INT(2),
  	avgPx BIGINT(15),
  	avgPx_scale INT(2),
  	lastPx BIGINT(15),
  	lastPx_scale INT(2),
  	lastQty BIGINT(15),
  	lastQty_scale INT(2),  
  	PRIMARY KEY (id)
);
CREATE INDEX `MESSAGE_LOG1` ON `MESSAGE_LOG` (`orderId`); 
CREATE INDEX `MESSAGE_LOG2` ON `MESSAGE_LOG` (`account`); 
CREATE INDEX `MESSAGE_LOG3` ON `MESSAGE_LOG` (`sequence_number`); 

create table SECURITY_DEFINITION_LOG (
  	id BIGINT(15) NOT NULL AUTO_INCREMENT,
  	sequence_number BIGINT(15),
  	insert_time VARCHAR(32) DEFAULT NULL,
  	updateType INT(1),
  	securityId INT(8),
  	symbol VARCHAR(32),
  	name VARCHAR(128),
  	assetType INT(1),
  	quotedId INT(8),
  	baseId INT(8),
  	priceScale INT(8),
  	quantityScale INT(8),
  	orderBookStrategy INT(4),
  	preOrderCheckStrategy INT(4),
  	settleType int default 0,
	maintMarginPercent int default 100,
	requiredMarginPercent int default 100,
	usdMark float,
  	PRIMARY KEY (id)
 );
 
 CREATE INDEX `SECURITY_DEFINITION_LOG1` ON `SECURITY_DEFINITION_LOG` (`sequence_number`); 

 

 
create table USER_LOG (
  	id BIGINT(15) NOT NULL AUTO_INCREMENT,
  	sequence_number BIGINT(15),
  	insert_time VARCHAR(32) DEFAULT NULL,
  	userId BIGINT(16),
	username VARCHAR(128),
	password VARCHAR(128),
	firmId INT(8),
	feeTier INT(8),
	requestStatus INT(1),
	lmm INT(1),
	registeredIP VARCHAR(32),
 	lastIP VARCHAR(32),
 	verification INT(8),
 	referral_code VARCHAR(256),
 	referred_by_code VARCHAR(256),
  	PRIMARY KEY (id)
 );

 
 create table FEE_LOG (
  	id BIGINT(15) NOT NULL AUTO_INCREMENT,
  	sequence_number BIGINT(15),
  	insert_time VARCHAR(32) DEFAULT NULL,
  	updateType INT(1),
  	assetId INT(8),
  	feeInstrumentId INT(8),
  	fee BIGINT(16),
  	feeType INT(1),
  	makerTaker INT(1),
  	tier INT(8),
  	PRIMARY KEY (id)
 );

 create table BALANCE_LOG (
  	id BIGINT(15) NOT NULL AUTO_INCREMENT,
  	sequence_number BIGINT(15),
  	insert_time VARCHAR(32) DEFAULT NULL,
  	updateType INT(1),
  	userId BIGINT(16),
  	firmId INT(8),
  	feeTier INT(8),
  	assetId INT(8),
  	balance BIGINT(16),
  	balance_scale INT(4),
  	balance_change BIGINT(16),
  	balance_change_scale INT(4),
  	event_type INT(4),
  	orderId BIGINT(15),
  	execId BIGINT(15),
  	unrealizedUsd FLOAT(20,8),
  	realizedUsd FLOAT(20,8),
  	avgCostBasisUsd FLOAT(20,8),
  	baseUsdMark FLOAT(20,8),
  	settleCoinUsdMark FLOAT(20,8),
  	settleCoinUnrealized FLOAT(20,8),
  	settleCoinRealized FLOAT(20,8),  	
  	PRIMARY KEY (id)
 );
  CREATE INDEX `BALANCE_LOG1` ON `BALANCE_LOG` (`userId`); 
 CREATE INDEX `BALANCE_LOG2` ON `BALANCE_LOG` (`assetId`); 
 
  create table FUND_BALANCE_LOG (
  	id BIGINT(15) NOT NULL AUTO_INCREMENT,
  	sequence_number BIGINT(15),
  	insert_time VARCHAR(32) DEFAULT NULL,
  	updateType INT(1),
  	userId BIGINT(16),
  	account_id BIGINT(16),
  	firmId INT(8),
  	feeTier INT(8),
  	assetId INT(8),
  	balance BIGINT(16),
  	balance_scale INT(4),
  	balance_change BIGINT(16),
  	balance_change_scale INT(4),
  	quantity BIGINT(16),
  	quantity_scale INT(4),
  	price BIGINT(16),
  	price_scale INT(4),
  	event_type INT(4),
  	orderId BIGINT(15),
  	execId BIGINT(15),
  	unrealizedUsd FLOAT(20,8),
  	realizedUsd FLOAT(20,8),
  	avgCostBasisUsd FLOAT(20,8),
  	baseUsdMark FLOAT(20,8),
  	settleCoinUsdMark FLOAT(20,8),
  	settleCoinUnrealized FLOAT(20,8),
  	settleCoinRealized FLOAT(20,8),  
  	fund_outstanding BIGINT(16),
  	fund_outstanding_scale INT(4),
  	side CHAR(3),
  	PRIMARY KEY (id)
 );
 CREATE INDEX `FUND_BALANCE_LOG1` ON `FUND_BALANCE_LOG` (`userId`); 
 CREATE INDEX `FUND_BALANCE_LOG2` ON `FUND_BALANCE_LOG` (`assetId`); 

 
create table ORDER_BOOK_STATE (
  	id BIGINT(15) NOT NULL AUTO_INCREMENT,
  	sequence_number BIGINT(15),
  	active INT(1),
  	insert_time VARCHAR(32) DEFAULT NULL,
  	messageType CHAR(3),
  	orderId BIGINT(15),
  	execId BIGINT(15),
  	clOrdId VARCHAR(32) DEFAULT NULL,
  	securityId INT(8),
  	symbol VARCHAR(32),
  	side CHAR(3),
  	ordType CHAR(3),
  	execType CHAR(3),
  	execRestatementReason CHAR(3),
  	ordStatus CHAR(3),
  	account INT(12),
  	timeInForce CHAR(1),
  	expireTime VARCHAR(32) DEFAULT NULL,
  	timestamp VARCHAR(32) DEFAULT NULL,
  	orderQty BIGINT(15),
  	orderQty_scale INT(2),
  	leavesQty BIGINT(15),
  	leavesQty_scale INT(2),
  	cumQty BIGINT(15),
  	cumQty_scale INT(2),
  	price BIGINT(15),
  	price_scale INT(2),
  	avgPx BIGINT(15),
  	avgPx_scale INT(2),
  	lastPx BIGINT(15),
  	lastPx_scale INT(2),
  	lastQty BIGINT(15),
  	lastQty_scale INT(2),
  	stopPx BIGINT(15),
  	stopPx_scale INT(2),
  	senderCompId VARCHAR(64) DEFAULT NULL,
  	feePositionId INT(12),
  	feePositionQuantity BIGINT(15),
  	feePositionQuantityChange BIGINT(15),
  	PRIMARY KEY (id)
);

CREATE INDEX `ORDER_BOOK_STATE1` ON `ORDER_BOOK_STATE` (`orderId`); 
CREATE INDEX `ORDER_BOOK_STATE2` ON `ORDER_BOOK_STATE` (`securityId`); 
CREATE INDEX `ORDER_BOOK_STATE3` ON `ORDER_BOOK_STATE` (`active`); 
CREATE INDEX `ORDER_BOOK_STATE4` ON `ORDER_BOOK_STATE` (`account`); 

create table BALANCE_STATE (
  	id BIGINT(15) NOT NULL AUTO_INCREMENT,
  	sequence_number BIGINT(15),
  	insert_time VARCHAR(32) DEFAULT NULL,
  	updateType INT(1),
  	userId BIGINT(16),
  	firmId INT(8),
  	feeTier INT(8),
  	assetId INT(8),
  	balance BIGINT(18),
  	balance_scale INT(4),
  	
  	unrealizedUsd FLOAT(20,8),
  	realizedUsd FLOAT(20,8),
  	avgCostBasisUsd FLOAT(20,8),
  	baseUsdMark FLOAT(20,8),
  	settleCoinUsdMark FLOAT(20,8),
  	settleCoinUnrealized FLOAT(20,8),
  	settleCoinRealized FLOAT(20,8), 
  	
  	PRIMARY KEY (id)
 );
 
 CREATE INDEX `BALANCE_STATE1` ON `BALANCE_STATE` (`userId`); 
CREATE INDEX `BALANCE_STATE2` ON `BALANCE_STATE` (`assetId`); 
 




 create table USER_STATE (
  	id BIGINT(15) NOT NULL AUTO_INCREMENT,
  	sequence_number BIGINT(15),
  	insert_time VARCHAR(32) DEFAULT NULL,
	username VARCHAR(128),
	password VARCHAR(128),
	requestToken VARCHAR(128),
	requestSecret VARCHAR(128),
	email VARCHAR(128),
	phone VARCHAR(32),
	firmId INT(8),
	feeTier INT(8),
	requestStatus INT(1),
	lmm INT(1),
	senderCompId VARCHAR(128),
	targetCompId VARCHAR(128),
	registeredIP VARCHAR(32),
 	lastIP VARCHAR(32),
 	walletRequestToken VARCHAR(128),
	walletRequestSecret VARCHAR(128),
	verification INT(8),
  	PRIMARY KEY (id)
 );
 
 alter table USER_STATE add(
 referral_code VARCHAR(128), 
 referred_by_code VARCHAR(128),
 use2auth INT(1),
 authToken VARCHAR(128),
 authUrl VARCHAR(512),
 companyname VARCHAR(256),
 firstname VARCHAR(256),
 middlename VARCHAR(256),
 lastname VARCHAR(256),
 address1 VARCHAR(256),
 address2 VARCHAR(256),
 address3 VARCHAR(256),
 city VARCHAR(128),
 state VARCHAR(128),
 zip VARCHAR(32),
 country VARCHAR(64),
 birthdate VARCHAR(32),
 taxid VARCHAR(32),
 requestCount INT(8),
 orderCount INT(8),
 linkedin VARCHAR(128),
 facebook VARCHAR(128),
 skype VARCHAR(128),
 photo_url VARCHAR(128),
 icon_url VARCHAR(128),
 id_url1 VARCHAR(128),
 id_url2 VARCHAR(128),
 id_url3 VARCHAR(128),
 id_url4 VARCHAR(128),
 id_url5 VARCHAR(128),
 security_question1 VARCHAR(256),
 security_answer1 VARCHAR(256),
 security_question2 VARCHAR(256),
 security_answer2 VARCHAR(256),
 security_question3 VARCHAR(256),
 security_answer3 VARCHAR(256),
 security_question4 VARCHAR(256),
 security_answer4 VARCHAR(256),
 security_question5 VARCHAR(256),
 security_answer5 VARCHAR(256),
 emergency_contact_name VARCHAR(128),
 emergency_contact_email VARCHAR(128),
 emergency_contact_phone VARCHAR(128),
 emergency_btc_address VARCHAR(128),
 type VARCHAR(64)
 );
  CREATE INDEX `USER_STATE1` ON `USER_STATE` (`email`); 
 CREATE INDEX `USER_STATE2` ON `USER_STATE` (`requestToken`);

 create table TRADE_HISTORY_LOG (
  	id BIGINT(15) NOT NULL AUTO_INCREMENT,
  	timeMillis VARCHAR(18),
  	securityId INT(8),
  	price BIGINT(15),
  	quantity BIGINT(15),
  	side CHAR(3),
   	PRIMARY KEY (id)
 );
 

   create table WITHDRAW_REQUEST (
  	id BIGINT(15) NOT NULL AUTO_INCREMENT,
  	sequence_number BIGINT(15),
  	insert_time VARCHAR(32) DEFAULT NULL,
  	address VARCHAR(64),
  	updateType INT(1),
  	userId BIGINT(16),
  	assetId INT(8),
  	symbol VARCHAR(16),
  	balance_change BIGINT(16),
  	balance_change_scale INT(4),
  	balance BIGINT(16),
  	balance_scale INT(4),
  	confirms INT(8),
  	source VARCHAR(256),
  	updateBy VARCHAR(64),
  	signature VARCHAR(256),
  	status INT(4),
  	ip VARCHAR(32),
  	PRIMARY KEY (id)
 );
  CREATE INDEX `WITHDRAW_REQUEST1` ON `WITHDRAW_REQUEST` (`userId`); 
CREATE INDEX `WITHDRAW_REQUEST2` ON `WITHDRAW_REQUEST` (`assetId`); 
CREATE INDEX `WITHDRAW_REQUEST3` ON `WITHDRAW_REQUEST` (`status`);

 
  create table ADDRESS_STATE_LOG (
  	id BIGINT(15) NOT NULL AUTO_INCREMENT,
  	sequence_number BIGINT(15),
  	insert_time VARCHAR(32) DEFAULT NULL,
  	address VARCHAR(64),
  	updateType INT(1),
  	userId BIGINT(16),
  	assetId INT(8),
  	symbol VARCHAR(16),
  	balance_change BIGINT(16),
  	balance_change_scale INT(4),
  	balance BIGINT(16),
  	balance_scale INT(4),
  	confirms INT(8),
  	source VARCHAR(256),
  	updateBy VARCHAR(64),
  	signature VARCHAR(256),
  	PRIMARY KEY (id)
 );
 



 create table ADDRESSES (
  	id BIGINT(15) NOT NULL AUTO_INCREMENT,
  	address VARCHAR(64),
  	userId BIGINT(16),
  	assetId INT(8),
  	symbol VARCHAR(16),
  	signature VARCHAR(256),
    PRIMARY KEY (id)
 );	
  	
  create table UPLOAD_FILE (
  	id BIGINT(15) NOT NULL AUTO_INCREMENT,
  	userId BIGINT(16),
  	file_key VARCHAR(128),
  	filename VARCHAR(128),
  	content_type VARCHAR(32),
  	proxy_location VARCHAR(128),  	
  	created TIMESTAMP default now(),
    PRIMARY KEY (id)
 );
  CREATE INDEX `UPLOAD_FILE1` ON `UPLOAD_FILE` (`file_key`); 

 create table GEO_IP (
  	id BIGINT(15) NOT NULL AUTO_INCREMENT,
  	ip VARCHAR(64),
  	continentName VARCHAR(64),
  	countryCode VARCHAR(64),
  	countryName VARCHAR(64),
  	regionCode VARCHAR(64),
  	regionName VARCHAR(64),
  	city VARCHAR(64),
  	zip VARCHAR(64),
  	latitude VARCHAR(64),
  	longitude VARCHAR(64),
  	geonameId VARCHAR(64),
  	status INT(4),
  	PRIMARY KEY (id)
 );
  CREATE INDEX `GEO_IP1` ON `GEO_IP` (`ip`); 
CREATE INDEX `GEO_IP2` ON `GEO_IP` (`countryName`); 
 CREATE INDEX `GEO_IP3` ON `GEO_IP` (`status`); 

     

  create table CHAIN_TRANSACTION_LOG (
  	id BIGINT(15) NOT NULL AUTO_INCREMENT,
  	userId BIGINT(15),
  	instrumentId INT,
  	symbol VARCHAR(64),
  	address VARCHAR(256),
  	transactionId VARCHAR(256),
  	balance double precision,
  	balance_change double precision,
  	confirmations INT,
  	transaction_time VARCHAR(64),
  	created TIMESTAMP default now(),
  	ip VARCHAR(64),
  	source VARCHAR(64),  	
  	status INT(4),
  	sent double precision,
  	received double precision,
  	tx_count INT,
  	unconfirmed_tx_count INT,
  	unspent_tx_count INT,  	
  	unconfirmed_received double precision,
  	unconfirmed_sent double precision,
  	first_tx VARCHAR(256),
  	last_tx VARCHAR(256),
  	watcher_timestamp VARCHAR(64),
  	transactionType VARCHAR(32),
  	note VARCHAR(256),
  	
  	PRIMARY KEY (id)
 );
  CREATE INDEX `CHAIN_TRANSACTION_LOG1` ON `CHAIN_TRANSACTION_LOG` (`userId`); 
CREATE INDEX `CHAIN_TRANSACTION_LOG2` ON `CHAIN_TRANSACTION_LOG` (`instrumentId`); 
 CREATE INDEX `CHAIN_TRANSACTION_LOG3` ON `CHAIN_TRANSACTION_LOG` (`address`); 

 
  create table FUND (
  	id BIGINT(15) NOT NULL AUTO_INCREMENT,
  	user_id BIGINT(15),
  	user_manager_id BIGINT(15),
  	title VARCHAR(128),
  	description VARCHAR(2048),
  	keywords VARCHAR(2048),
  	image_url VARCHAR(256),
  	overview VARCHAR(2048),
  	manager_description VARCHAR(2048),
  	manager_fee FLOAT(20,8),
  	solfini_fee FLOAT(20,8),
  	manager_stake FLOAT(20,8),
  	created TIMESTAMP default now(),
  	last_updated TIMESTAMP default now(),
  	volatility FLOAT(20,8),
  	target_return FLOAT(20,8),
  	target_interest FLOAT(20,8),
  	nav FLOAT(20,8),
  	outstanding FLOAT(20,8),
  	inception_return FLOAT(20,8),
  	d1_return FLOAT(20,8),
  	d30_return FLOAT(20,8),
  	holding1 INT,
  	holding1_percent FLOAT(20,8),
  	holding1_name VARCHAR(64),
    holding2 INT,
  	holding2_percent FLOAT(20,8),
  	holding2_name VARCHAR(64),
  	holding3 INT,
  	holding3_percent FLOAT(20,8),
  	holding3_name VARCHAR(64),
  	holding4 INT,
  	holding4_percent FLOAT(20,8),
  	holding4_name VARCHAR(64),
  	holding5 INT,
  	holding5_percent FLOAT(20,8),
  	holding5_name VARCHAR(64),
  	status INT(4),
  	strategy VARCHAR(256),
  	yr1Return FLOAT(20,8),
  	valuation FLOAT(20,8),
  	divYield FLOAT(20,8),
  	benchmark FLOAT(20,8),
  	account_id BIGINT(15),
  	PRIMARY KEY (id)
 );
 
 
 alter table USER_STATE add(
 registerCode VARCHAR(128) DEFAULT NULL,
 lastWithdrawCode VARCHAR(128) DEFAULT NULL,
 useCodeValidaton INT DEFAULT 1
);


 
   CREATE INDEX `FUND1` ON `FUND` (`user_id`); 
   CREATE INDEX `FUND2` ON `FUND` (`user_manager_id`); 
   CREATE INDEX `FUND3` ON `FUND` (`holding1`); 

INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (1,1,'20180507-00:09:58.756',1,1,'USD','USD',0,0,0,2,2,2,11,0,100,100,1.0,0);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (2,2,'20180507-00:09:58.756',1,2,'ETH','ETH',0,0,0,2,6,2,11,0,100,100,205.0,1);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (3,3,'20180507-00:09:58.756',1,3,'BTC','BTC',0,0,0,2,6,2,11,0,100,100,6500.0,1);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (4,4,'20180507-00:09:58.756',1,4,'ETH/USD','ETH/USD',1,2,1,2,6,2,11,0,100,100,235.0,0);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (5,5,'20180507-00:09:58.756',1,5,'BTC/USD','BTC/USD',1,3,1,2,6,2,11,0,100,100,6700.0,0);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (6,6,'20180507-00:09:58.756',1,6,'BTCTEST','BTCTEST',0,0,0,2,6,2,11,0,100,100,6700.0,0);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (7,7,'20180507-00:09:58.756',1,7,'ETHTEST','ETHTEST',0,0,0,2,6,2,11,0,100,100,235.0,0);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (20,9,'20180507-00:09:58.756',1,9,'ETH/USD[F]','ETH/USD[F]',1,2,1,2,6,2,12,1,10,20,150.0,1);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (21,10,'20180507-00:09:58.756',1,10,'LTC','LTC',0,0,0,2,6,2,11,0,100,100,1200.0,1);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (22,11,'20180507-00:09:58.756',1,11,'GLD/USD[F]','GLD/USD[F]',1,3,1,2,6,2,12,1,10,20,1200.0,1);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (23,12,'20180507-00:09:58.756',1,12,'BTC/USD[F]','BTC/USD[F]',1,3,1,2,6,2,12,1,10,20,7000.0,1);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (24,13,'20180507-00:09:58.756',1,13,'SLV/USD[F]','SLV/USD[F]',1,3,1,2,6,2,12,1,10,20,15.0,1);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (25,14,'20180507-00:09:58.756',1,14,'SPY/USD[F]','SPY/USD[F]',1,3,1,2,6,2,12,1,10,20,280.0,1);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (26,15,'20180507-00:09:58.756',1,15,'ETH/BTC','ETH/BTC',1,2,3,6,6,2,11,0,100,100,100.0,1);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (27,16,'20180507-00:09:58.756',1,16,'LTC/BTC','LTC/BTC',1,10,3,6,6,2,11,0,100,100,100.0,1);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (28,17,'20180507-00:09:58.756',1,17,'QQQ/USD[F]','QQQ/USD[F]',1,3,1,2,6,2,12,1,10,20,280.0,1);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (29,18,'20180507-00:09:58.756',1,18,'AAPL/USD[F]','AAPL/USD[F]',1,3,1,2,6,2,12,1,10,20,280.0,1);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (30,19,'20180507-00:09:58.756',1,19,'GOOG/USD[F]','GOOG/USD[F]',1,3,1,2,6,2,12,1,10,20,280.0,1);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (31,20,'20180507-00:09:58.756',1,20,'FB/USD[F]','FB/USD[F]',1,3,1,2,6,2,12,1,10,20,280.0,1);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (32,21,'20180507-00:09:58.756',1,21,'USO/USD[F]','USO/USD[F]',1,3,1,2,6,2,12,1,10,20,280.0,1);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (33,22,'20180507-00:09:58.756',1,22,'EWJ/USD[F]','EWJ/USD[F]',1,3,1,2,6,2,12,1,10,20,280.0,1);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (34,23,'20180507-00:09:58.756',1,23,'MCHI/USD[F]','MCHI/USD[F]',1,3,1,2,6,2,12,1,10,20,280.0,1);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (35,24,'20180507-00:09:58.756',1,24,'LMM','LMM',0,0,0,2,6,2,11,0,100,100,1000.0,1);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (36,25,'20180507-00:09:58.756',1,25,'LMM/USD','LMM/USD',1,24,1,2,6,2,11,0,100,100,1000.0,1);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (37,26,'20180507-00:09:58.756',1,26,'LMM/BTC','LMM/BTC',1,24,3,2,6,2,11,0,100,100,20.0,1);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (38,27,'20180507-00:09:58.756',1,27,'LMM/ETH','LMM/ETH',1,24,2,2,6,2,11,0,100,100,20.0,1);


INSERT INTO USER_STATE (id,sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId) VALUES (1,0,'20180510-23:09:54.186','chris','7rRy/iYpZEIaZs/n4coLNw==','requestToken','requestSecret',null,null,5,0,0,0,'123456','test');
INSERT INTO USER_STATE (id,sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId) VALUES (2,0,'20180510-23:56:18.448','steve','7rRy/iYpZEIaZs/n4coLNw==','requestToken','requestSecret',null,null,5,0,0,0,'123456','test');
INSERT INTO USER_STATE (id,sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId) VALUES (3,0,'20180511-00:31:48.224','raakhee','7rRy/iYpZEIaZs/n4coLNw==','requestToken','requestSecret',null,null,5,0,0,0,'123456','test');
INSERT INTO USER_STATE (id,sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId) VALUES (8,0,'20180514-00:29:02.630','bot','7rRy/iYpZEIaZs/n4coLNw==','requestToken4','requestSecret4','brandon.wahl.sit@gmail.com','555-5555',1,0,0,1,'1000000008','test');
INSERT INTO USER_STATE (id,sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId) VALUES (9,0,'20180514-00:34:25.358','tradeapi','7rRy/iYpZEIaZs/n4coLNw==','requestToken5','requestSecret5','brandon.wahl.sit@gmail.com','555-5555',1,0,0,0,'1000000009','test');
INSERT INTO USER_STATE (id,sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId) VALUES (10,0,'20180514-00:37:56.152','admin4','VsGtFB9HQnk=','CzMt4Ap6I86BMzXP','Ywww9GBF2Mtiu4iL','brandon.wahl.sit@gmail.com','555-5555',1,0,0,0,'1000000010',null);
INSERT INTO USER_STATE (id,sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId) VALUES (11,0,'20180514-00:40:26.782','admin5','FPkorqa1URo=','06uB79rXbULdEARG','62oIoIZETQxcZqS7','brandon.wahl.sit@gmail.com','555-5555"',1,0,0,0,'1000000011',null);
INSERT INTO USER_STATE (id,sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId) VALUES (12,0,'20180514-00:44:18.597','admin6','k0nR13mIsJk=','RnY3qWUVwqBrWTqW','KK2DAXiKPFUxgzhC','brandon.wahl.sit@gmail.com','555-5555"',1,0,0,0,'1000000012',null);
INSERT INTO USER_STATE (id,sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId) VALUES (13,0,'20180514-01:51:56.616','admin7','iA3bssVBzkQ=','QiaNRAyo3xWVdkIz','YDQvSpAxn5lEgDxO','brandon.wahl.sit@gmail.com','555-5555',1,0,0,0,'1000000013',null);
INSERT INTO USER_STATE (id,sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId) VALUES (14,0,'20180514-22:32:57.475','admin8','OdckERCTZUQ=','eMsDT15Efy15mDAV','FtnGpN2rNo2T4N3o','brandon.wahl.sit@gmail.com','555-5555',1,0,0,0,'1000000014',null);
INSERT INTO USER_STATE (id,sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId) VALUES (15,0,'20180516-22:45:48.185','admin9','6L2Y+wlA7q0=','hvHUQ3lYHYkcbat1','jHvgcR73Wa9MOish','brandon.wahl.sit@gmail.com','555-5555',1,0,0,0,'1000000015',null);
INSERT INTO USER_STATE (id,sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId) VALUES (16,0,'20180518-01:55:14.404','admin10','WQtJri+Mj2c=','FCSRXULDj0jzB1cl','3z7SuvXAA7woDeWL','brandon.wahl.sit@gmail.com','555-5555',1,0,0,0,'1000000016',null);
INSERT INTO USER_STATE (id,sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId) VALUES (17,0,'20180518-01:55:40.870','admin11','nqRbO3kvOu0=','3LuAAabc7KcRxaWL','q8nSIJNFlViFuAyc','brandon.wahl.sit@gmail.com','555-5555',1,0,0,0,'1000000017',null);
INSERT INTO USER_STATE (id,sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId) VALUES (18,0,'20180518-02:11:17.259','test','J0CTByiQMRA=','bJA90Or1EgCjFOL0','Yc0fNZRxKX4ULCuc','commoditybull@gmail.com','6316977749',0,1,0,0,'1000000018','test');
INSERT INTO USER_STATE (id,sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId) VALUES (19,0,'20180518-02:12:50.445','bwahl','J0CTByiQMRA=','4HghEjyYCp3zCN11','DTzgqSA5NRRD4fzH','brandon.wahl.sit@gmail.com','6316977749',0,1,0,0,'1000000019','test');
INSERT INTO USER_STATE (id,sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId) VALUES (20,0,'20180622-09:57:28.720','anil','+CTj291Qluw=','Marpjp6VkrlYfGY9','u11Tgu4Pck4STDqs','anil@gmail.co','8209098732',0,0,0,0,'1000000020',null);
INSERT INTO USER_STATE (id,sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId) VALUES (21,0,'20180725-06:54:47.684','test1','RHGDsnJvBjA=','MrtKclONXPx9YkJi','JpcgU28cUfHX2OYB','test1@gmail.com','8989898989',0,0,0,0,'1000000021',null);
INSERT INTO USER_STATE (id,sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId) VALUES (22,0,'20180725-06:58:32.207','s','KH0Q/NnvyG4=','WmXHKUUoA12yam9E','9zrXqLspPl0vWRRs','s@s.com','s',0,0,0,0,'1000000022',null);

  


INSERT INTO USER_LOG (id,sequence_number,insert_time,userId,username,password,firmId,feeTier,requestStatus,lmm) VALUES (1,0,'20180725-06:54:47.740',1,'test1','RHGDsnJvBjA=',0,0,0,0);
INSERT INTO USER_LOG (id,sequence_number,insert_time,userId,username,password,firmId,feeTier,requestStatus,lmm) VALUES (2,0,'20180725-06:58:32.211',1,'s','KH0Q/NnvyG4=',0,0,0,0);




INSERT INTO BALANCE_STATE (id,sequence_number,insert_time,updateType,userId,firmId,feeTier,assetId,balance,balance_scale,unrealizedUsd,realizedUsd,avgCostBasisUsd,baseUsdMark,settleCoinUsdMark,settleCoinUnrealized,settleCoinRealized) VALUES (1,0,'20180729-05:16:25.023',1,8,1,0,1,1638534974,2,0.0,0.0,0.0,0.0,0.0,0.0,0.0);
INSERT INTO BALANCE_STATE (id,sequence_number,insert_time,updateType,userId,firmId,feeTier,assetId,balance,balance_scale,unrealizedUsd,realizedUsd,avgCostBasisUsd,baseUsdMark,settleCoinUsdMark,settleCoinUnrealized,settleCoinRealized) VALUES (2,0,'20180729-05:16:25.023',1,8,1,0,2,989785618,6,0.0,0.0,0.0,0.0,0.0,0.0,0.0);
INSERT INTO BALANCE_STATE (id,sequence_number,insert_time,updateType,userId,firmId,feeTier,assetId,balance,balance_scale,unrealizedUsd,realizedUsd,avgCostBasisUsd,baseUsdMark,settleCoinUsdMark,settleCoinUnrealized,settleCoinRealized) VALUES (3,0,'20180729-05:16:25.023',1,8,1,0,3,989289410,6,0.0,0.0,0.0,0.0,0.0,0.0,0.0);
INSERT INTO BALANCE_STATE (id,sequence_number,insert_time,updateType,userId,firmId,feeTier,assetId,balance,balance_scale,unrealizedUsd,realizedUsd,avgCostBasisUsd,baseUsdMark,settleCoinUsdMark,settleCoinUnrealized,settleCoinRealized) VALUES (7,0,'20180729-04:19:26.941',1,18,0,0,1,343250627,2,0.0,0.0,0.0,0.0,0.0,0.0,0.0);
INSERT INTO BALANCE_STATE (id,sequence_number,insert_time,updateType,userId,firmId,feeTier,assetId,balance,balance_scale,unrealizedUsd,realizedUsd,avgCostBasisUsd,baseUsdMark,settleCoinUsdMark,settleCoinUnrealized,settleCoinRealized) VALUES (11,0,'20180729-04:19:26.941',1,18,0,0,2,1797060183,6,0.0,0.0,0.0,0.0,0.0,0.0,0.0);
INSERT INTO BALANCE_STATE (id,sequence_number,insert_time,updateType,userId,firmId,feeTier,assetId,balance,balance_scale,unrealizedUsd,realizedUsd,avgCostBasisUsd,baseUsdMark,settleCoinUsdMark,settleCoinUnrealized,settleCoinRealized) VALUES (12,0,'20180729-04:19:26.941',1,18,0,0,3,601266844,6,0.0,0.0,0.0,0.0,0.0,0.0,0.0);
INSERT INTO BALANCE_STATE (id,sequence_number,insert_time,updateType,userId,firmId,feeTier,assetId,balance,balance_scale,unrealizedUsd,realizedUsd,avgCostBasisUsd,baseUsdMark,settleCoinUsdMark,settleCoinUnrealized,settleCoinRealized) VALUES (15,0,'20180725-09:56:52.086',1,22,0,0,1,1433651761,2,0.0,0.0,0.0,0.0,0.0,0.0,0.0);
INSERT INTO BALANCE_STATE (id,sequence_number,insert_time,updateType,userId,firmId,feeTier,assetId,balance,balance_scale,unrealizedUsd,realizedUsd,avgCostBasisUsd,baseUsdMark,settleCoinUsdMark,settleCoinUnrealized,settleCoinRealized) VALUES (16,0,'20180725-09:56:52.086',1,22,0,0,2,1796848301,2,0.0,0.0,0.0,0.0,0.0,0.0,0.0);
INSERT INTO BALANCE_STATE (id,sequence_number,insert_time,updateType,userId,firmId,feeTier,assetId,balance,balance_scale,unrealizedUsd,realizedUsd,avgCostBasisUsd,baseUsdMark,settleCoinUsdMark,settleCoinUnrealized,settleCoinRealized) VALUES (17,0,'20180725-09:56:52.086',1,22,0,0,3,600556844,2,0.0,0.0,0.0,0.0,0.0,0.0,0.0);

INSERT INTO BALANCE_STATE (id,sequence_number,insert_time,updateType,userId,firmId,feeTier,assetId,balance,balance_scale,unrealizedUsd,realizedUsd,avgCostBasisUsd,baseUsdMark,settleCoinUsdMark,settleCoinUnrealized,settleCoinRealized) VALUES (13,0,'20180729-04:19:26.941',1,18,0,0,11,801266844,6,0.0,0.0,0.0,0.0,0.0,0.0,0.0);


INSERT INTO ADDRESS_STATE (sequence_number,insert_time,address,updateType,userId,assetId,symbol,balance,balance_scale,confirms,source,active,updateBy,signature,status) VALUES (0,'20180618-04:30:38.201','1DEP8i3QJCsomS4BSMY2RpU1upv62aGvhD',0,18,3,'BTC',0,0,0,null,1,null,null,null);
INSERT INTO ADDRESS_STATE (sequence_number,insert_time,address,updateType,userId,assetId,symbol,balance,balance_scale,confirms,source,active,updateBy,signature,status) VALUES (0,'20180618-04:30:40.116','1DEP8i3QJCsomS4BSMY2RpU1upv62aGvhD',0,0,0,'BTC',4449209,0,0,'blockexplorer.com',null,null,null,null);

INSERT INTO ADDRESS_STATE (sequence_number,insert_time,address,updateType,userId,assetId,symbol,balance,balance_scale,confirms,source,active,updateBy,signature,status) VALUES (0,'20180618-04:30:38.201','0xF4bc768958EbA4F12740b69f638579bdE6dAe3Df',0,18,7,'ETHTEST',0,0,0,null,1,null,null,null);
INSERT INTO ADDRESS_STATE (sequence_number,insert_time,address,updateType,userId,assetId,symbol,balance,balance_scale,confirms,source,active,updateBy,signature,status) VALUES (0,'20180618-04:30:38.201','0x5Ea20E7246663cfA51493BCbF79d670B31599e55',0,19,7,'ETHTEST',0,0,0,null,1,null,null,null);
INSERT INTO ADDRESS_STATE (sequence_number,insert_time,address,updateType,userId,assetId,symbol,balance,balance_scale,confirms,source,active,updateBy,signature,status) VALUES (0,'20180618-04:30:38.201','0xB8c9E67754634f590Ef0ffF5c2d6ec8e144454ED',0,20,7,'ETHTEST',0,0,0,null,1,null,null,null);
INSERT INTO ADDRESS_STATE (sequence_number,insert_time,address,updateType,userId,assetId,symbol,balance,balance_scale,confirms,source,active,updateBy,signature,status) VALUES (0,'20180618-04:30:38.201','0x208b6f21820691Fd3234c61b8F0F859AEEE0763C',0,21,7,'ETHTEST',0,0,0,null,1,null,null,null);




INSERT INTO ADDRESS_STATE_LOG (sequence_number,insert_time,address,updateType,userId,assetId,symbol,balance_change,balance_change_scale,balance,balance_scale,confirms,source,updateBy,signature) VALUES (0,'20180618-04:30:36.905','1DEP8i3QJCsomS4BSMY2RpU1upv62aGvhD',0,1,0,'BTC',0,0,0,0,0,null,null,null);
INSERT INTO ADDRESS_STATE_LOG (sequence_number,insert_time,address,updateType,userId,assetId,symbol,balance_change,balance_change_scale,balance,balance_scale,confirms,source,updateBy,signature) VALUES (0,'20180618-04:30:40.043','1DEP8i3QJCsomS4BSMY2RpU1upv62aGvhD',0,0,0,'BTC',4449209,0,4449209,0,0,'blockexplorer.com',null,null);




---------

INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,4,2,30,0,0,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,4,2,15,0,1,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,4,2,20,0,0,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,4,2,10,0,1,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,4,2,15,0,0,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,4,2,0,0,1,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,4,2,0,0,0,4);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,4,2,0,0,1,4);

INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,5,3,30,0,0,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,5,3,15,0,1,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,5,3,20,0,0,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,5,3,10,0,1,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,5,3,15,0,0,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,5,3,0,0,1,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,5,3,0,0,0,4);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,5,3,0,0,1,4);

INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,6,3,30,0,0,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,6,3,15,0,1,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,6,3,20,0,0,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,6,3,10,0,1,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,6,3,15,0,0,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,6,3,0,0,1,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,6,3,0,0,0,4);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,6,3,0,0,1,4);

INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,9,3,30,0,0,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,9,3,15,0,1,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,9,3,20,0,0,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,9,3,10,0,1,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,9,3,15,0,0,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,9,3,0,0,1,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,9,3,0,0,0,4);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,9,3,0,0,1,4);


INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,11,3,50,0,0,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,11,3,25,0,1,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,11,3,35,0,0,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,11,3,15,0,1,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,11,3,25,0,0,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,11,3,0,0,1,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,11,3,0,0,0,4);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,11,3,0,0,1,4);

INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,12,3,30,0,0,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,12,3,15,0,1,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,12,3,20,0,0,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,12,3,10,0,1,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,12,3,15,0,0,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,12,3,0,0,1,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,12,3,0,0,0,4);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,12,3,0,0,1,4);

INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,13,3,50,0,0,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,13,3,25,0,1,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,13,3,35,0,0,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,13,3,15,0,1,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,13,3,25,0,0,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,13,3,0,0,1,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,13,3,25,0,0,4);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,13,3,0,0,1,4);

INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,14,3,50,0,0,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,14,3,25,0,1,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,14,3,35,0,0,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,14,3,15,0,1,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,14,3,25,0,0,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,14,3,0,0,1,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,14,3,0,0,0,4);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,14,3,0,0,1,4);

INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,15,3,30,0,0,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,15,3,15,0,1,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,15,3,20,0,0,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,15,3,10,0,1,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,15,3,15,0,0,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,15,3,0,0,1,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,15,3,0,0,0,4);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,15,3,0,0,1,4);

INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,16,3,30,0,0,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,16,3,15,0,1,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,16,3,20,0,0,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,16,3,10,0,1,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,16,3,15,0,0,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,16,3,0,0,1,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,16,3,0,0,0,4);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,16,3,0,0,1,4);


INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,17,3,50,0,0,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,17,3,25,0,1,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,17,3,35,0,0,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,17,3,15,0,1,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,17,3,25,0,0,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,17,3,0,0,1,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,17,3,0,0,0,4);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,17,3,0,0,1,4);


INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,18,3,50,0,0,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,18,3,25,0,1,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,18,3,35,0,0,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,18,3,15,0,1,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,18,3,25,0,0,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,18,3,0,0,1,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,18,3,0,0,0,4);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,18,3,0,0,1,4);


INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,19,3,50,0,0,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,19,3,25,0,1,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,19,3,35,0,0,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,19,3,15,0,1,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,19,3,25,0,0,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,19,3,0,0,1,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,19,3,0,0,0,4);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,19,3,0,0,1,4);


INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,20,3,50,0,0,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,20,3,25,0,1,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,20,3,35,0,0,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,20,3,15,0,1,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,20,3,25,0,0,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,20,3,0,0,1,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,20,3,0,0,0,4);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,20,3,0,0,1,4);


INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,21,3,50,0,0,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,21,3,25,0,1,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,21,3,35,0,0,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,21,3,15,0,1,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,21,3,25,0,0,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,21,3,0,0,1,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,21,3,0,0,0,4);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,21,3,0,0,1,4);

INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,22,3,50,0,0,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,22,3,25,0,1,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,22,3,35,0,0,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,22,3,15,0,1,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,22,3,25,0,0,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,22,3,0,0,1,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,22,3,0,0,0,4);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,22,3,0,0,1,4);

INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,23,3,50,0,0,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,23,3,25,0,1,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,23,3,35,0,0,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,23,3,15,0,1,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,23,3,25,0,0,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,23,3,0,0,1,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,23,3,0,0,0,4);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,23,3,0,0,1,4);


INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,25,2,50,0,0,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,25,2,25,0,1,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,25,2,35,0,0,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,25,2,15,0,1,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,25,2,25,0,0,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,25,2,0,0,1,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,25,2,0,0,0,4);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,25,2,0,0,1,4);

INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,26,2,50,0,0,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,26,2,25,0,1,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,26,2,35,0,0,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,26,2,15,0,1,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,26,2,25,0,0,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,26,2,0,0,1,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,26,2,0,0,0,4);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,26,2,0,0,1,4);

INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,27,2,50,0,0,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,27,2,25,0,1,1);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,27,2,35,0,0,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,27,2,15,0,1,2);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,27,2,25,0,0,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,27,2,0,0,1,3);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,27,2,0,0,0,4);
INSERT INTO FEE_LOG (sequence_number,insert_time,updateType,assetId,feeInstrumentId,fee,feeType,makerTaker,tier) VALUES (null,'20180729-04:19:26.941',0,27,2,0,0,1,4);




alter table SECURITY_DEFINITION_LOG add status int
update SECURITY_DEFINITION_LOG set status=1
update SECURITY_DEFINITION_LOG set status=0 where securityId in(6,7,1)

INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (20,9,'20180507-00:09:58.756',1,9,'ETH/USD[F]','ETH/USD[F]',1,2,1,2,6,2,12,1,10,20,150.0,1);


CREATE TABLE user_trade_pnl (
    id BIGINT NOT NULL,
    user_id BIGINT,
    security_id BIGINT,
    symbol VARCHAR(32) DEFAULT NULL,
    running_position DOUBLE PRECISION,
    avg_cost DOUBLE PRECISION,
    realized_pnl DOUBLE PRECISION,
    notional DOUBLE PRECISION,
    total_notional DOUBLE PRECISION,
    trade_count BIGINT,
    created TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (id)
);

