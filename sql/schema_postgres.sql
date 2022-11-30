drop table if exists EMAIL_LOG;
drop table if exists MESSAGE_LOG;
drop table if exists TRADE_HISTORY_LOG;
drop table if exists ADDRESS_STATE;
drop table if exists ADDRESS_STATE_LOG;
drop table if exists BALANCE_LOG;
drop table if exists BALANCE_STATE;
drop table if exists FEE_LOG;
drop table if exists ORDER_BOOK_STATE;
drop table if exists SECURITY_DEFINITION_LOG;
drop table if exists USER_LOG;
drop table if exists USER_STATE;
drop table if exists WITHDRAW_REQUEST;
drop table if exists FUND;
drop table if exists GEO_IP;
drop table if exists ADDRESSES;
drop table if exists FUND_BALANCE_LOG;
drop table if exists UPLOAD_FILE;
drop table if exists chain_transaction_log;


create table EMAIL_LOG (
  	id SERIAL PRIMARY KEY NOT NULL,
  	email VARCHAR(128) DEFAULT NULL,
  	insert_time VARCHAR(32) DEFAULT NULL,  
  	ip VARCHAR(128) DEFAULT NULL
);
  	
create table MESSAGE_LOG (
  	id BIGSERIAL PRIMARY KEY NOT NULL,
   	sequence_number BIGINT,
  	insert_time VARCHAR(32) DEFAULT NULL,
  	messageType CHAR(3),
  	orderId BIGINT,
  	execId BIGINT,
  	clOrdId VARCHAR(32) DEFAULT NULL,
  	securityId INT,
  	symbol VARCHAR(32),
  	side CHAR(3),
  	ordType CHAR(3),
  	execType CHAR(3),
  	execRestatementReason CHAR(3),
  	ordStatus CHAR(3),
  	account INT,
  	timeInForce CHAR(1),
  	expireTime VARCHAR(32) DEFAULT NULL,
  	--timestamp VARCHAR(32) DEFAULT NULL,
  	orderQty BIGINT,
  	orderQty_scale SMALLINT,
  	leavesQty BIGINT,
  	leavesQty_scale SMALLINT,
  	cumQty BIGINT,
  	cumQty_scale SMALLINT,
  	price BIGINT,
  	price_scale SMALLINT,
  	avgPx BIGINT,
  	avgPx_scale SMALLINT,
  	lastPx BIGINT,
  	lastPx_scale SMALLINT,
  	lastQty BIGINT,
  	lastQty_scale SMALLINT 
);

CREATE INDEX MESSAGE_LOG1 ON MESSAGE_LOG (orderId); 
CREATE INDEX MESSAGE_LOG2 ON MESSAGE_LOG (account); 
CREATE INDEX MESSAGE_LOG3 ON MESSAGE_LOG (sequence_number); 

create table SECURITY_DEFINITION_LOG (
  	id SERIAL PRIMARY KEY NOT NULL,
  	sequence_number BIGINT,
  	insert_time VARCHAR(32) DEFAULT NULL,
  	updateType SMALLINT,
  	securityId INT,
  	symbol VARCHAR(32),
  	name VARCHAR(128),
  	assetType SMALLINT,
  	quotedId INT,
  	baseId INT,
  	priceScale INT,
  	quantityScale INT,
  	orderBookStrategy SMALLINT,
  	preOrderCheckStrategy SMALLINT,
  	settleType int default 0,
	maintMarginPercent int default 100,
	requiredMarginPercent int default 100,
	usdMark double precision,
	status int,
	estimatedUserCount int,
	daysFeedIsActive int,
	estimatedVolatility double precision,
	estimatedVAR double precision
 );

 
 CREATE INDEX SECURITY_DEFINITION_LOG1 ON SECURITY_DEFINITION_LOG (sequence_number); 

 

 
create table USER_LOG (
  	id BIGSERIAL PRIMARY KEY NOT NULL,
  	sequence_number BIGINT,
  	insert_time VARCHAR(32) DEFAULT NULL,
  	userId BIGINT,
	username VARCHAR(128),
	password VARCHAR(128),
	firmId INT,
	feeTier INT,
	requestStatus SMALLINT,
	lmm SMALLINT,
	registeredIP VARCHAR(32),
 	lastIP VARCHAR(32),
 	verification INT,
 	referral_code VARCHAR(256),
 	referred_by_code VARCHAR(256)
 );

 
 create table FEE_LOG (
	id BIGSERIAL PRIMARY KEY NOT NULL,
  	sequence_number BIGINT,
  	insert_time VARCHAR(32) DEFAULT NULL,
  	updateType SMALLINT,
  	assetId INT,
  	feeInstrumentId INT,
  	fee BIGINT,
  	feeType SMALLINT,
  	makerTaker SMALLINT,
  	tier INT
 );

 create table BALANCE_LOG (
	id BIGSERIAL PRIMARY KEY NOT NULL,
  	sequence_number BIGINT,
  	insert_time VARCHAR(32) DEFAULT NULL,
  	updateType SMALLINT,
  	userId BIGINT,
  	firmId INT,
  	feeTier INT,
  	assetId INT,
  	balance BIGINT,
  	balance_scale INT,
  	balance_change BIGINT,
  	balance_change_scale INT,
  	event_type INT,
  	orderId BIGINT,
  	execId BIGINT,
  	unrealizedUsd double precision,
  	realizedUsd double precision,
  	avgCostBasisUsd double precision,
  	baseUsdMark double precision,
  	settleCoinUsdMark double precision,
  	settleCoinUnrealized double precision,
  	settleCoinRealized double precision
 );
  CREATE INDEX BALANCE_LOG1 ON BALANCE_LOG (userId); 
 CREATE INDEX BALANCE_LOG2 ON BALANCE_LOG (assetId); 
 
  create table FUND_BALANCE_LOG (
  	id BIGSERIAL PRIMARY KEY NOT NULL,
  	sequence_number BIGINT,
  	insert_time VARCHAR(32) DEFAULT NULL,
  	updateType SMALLINT,
  	userId BIGINT,
  	account_id BIGINT,
  	firmId INT,
  	feeTier INT,
  	assetId INT,
  	balance BIGINT,
  	balance_scale INT,
  	balance_change BIGINT,
  	balance_change_scale INT,
  	quantity BIGINT,
  	quantity_scale INT,
  	price BIGINT,
  	price_scale INT,
  	event_type INT,
  	orderId BIGINT,
  	execId BIGINT,
  	unrealizedUsd double precision,
  	realizedUsd double precision,
  	avgCostBasisUsd double precision,
  	baseUsdMark double precision,
  	settleCoinUsdMark double precision,
  	settleCoinUnrealized double precision,
  	settleCoinRealized double precision,  
  	fund_outstanding BIGINT,
  	fund_outstanding_scale INT,
  	side CHAR(3),
  	usdTotalFundValue double precision
 );
 CREATE INDEX FUND_BALANCE_LOG1 ON FUND_BALANCE_LOG (userId); 
 CREATE INDEX FUND_BALANCE_LOG2 ON FUND_BALANCE_LOG (assetId); 

 
create table ORDER_BOOK_STATE (
  	id BIGSERIAL PRIMARY KEY NOT NULL,
  	sequence_number BIGINT,
  	active SMALLINT,
  	insert_time VARCHAR(32) DEFAULT NULL,
  	messageType CHAR(3),
  	orderId BIGINT,
  	execId BIGINT,
  	clOrdId VARCHAR(32) DEFAULT NULL,
  	securityId INT,
  	symbol VARCHAR(32),
  	side CHAR(3),
  	ordType CHAR(3),
  	execType CHAR(3),
  	execRestatementReason CHAR(3),
  	ordStatus CHAR(3),
  	account BIGINT,
  	timeInForce CHAR(1),
  	expireTime VARCHAR(32) DEFAULT NULL,
  	timestamp VARCHAR(32) DEFAULT NULL,
  	orderQty BIGINT,
  	orderQty_scale INT,
  	leavesQty BIGINT,
  	leavesQty_scale SMALLINT,
  	cumQty BIGINT,
  	cumQty_scale SMALLINT,
  	price BIGINT,
  	price_scale SMALLINT,
  	avgPx BIGINT,
  	avgPx_scale SMALLINT,
  	lastPx BIGINT,
  	lastPx_scale SMALLINT,
  	lastQty BIGINT,
  	lastQty_scale SMALLINT,
  	stopPx BIGINT,
  	stopPx_scale SMALLINT,
  	senderCompId VARCHAR(64) DEFAULT NULL,
  	feePositionId BIGINT,
  	feePositionQuantity BIGINT,
  	feePositionQuantityChange BIGINT
);

CREATE INDEX ORDER_BOOK_STATE1 ON ORDER_BOOK_STATE (orderId); 
CREATE INDEX ORDER_BOOK_STATE2 ON ORDER_BOOK_STATE (securityId); 
CREATE INDEX ORDER_BOOK_STATE3 ON ORDER_BOOK_STATE (active); 
CREATE INDEX ORDER_BOOK_STATE4 ON ORDER_BOOK_STATE (account); 

create table BALANCE_STATE (
  	id BIGSERIAL PRIMARY KEY NOT NULL,
  	sequence_number BIGINT,
  	insert_time VARCHAR(32) DEFAULT NULL,
  	updateType SMALLINT,
  	userId BIGINT,
  	firmId INT,
  	feeTier INT,
  	assetId INT,
  	balance BIGINT,
  	balance_scale INT,
  	
  	unrealizedUsd double precision,
  	realizedUsd double precision,
  	avgCostBasisUsd double precision,
  	baseUsdMark double precision,
  	settleCoinUsdMark double precision,
  	settleCoinUnrealized double precision,
  	settleCoinRealized double precision
 );
 
 CREATE INDEX BALANCE_STATE1 ON BALANCE_STATE (userId); 
CREATE INDEX BALANCE_STATE2 ON BALANCE_STATE (assetId); 
 
 create table USER_STATE (
  	id BIGSERIAL PRIMARY KEY NOT NULL,
  	sequence_number BIGINT,
  	insert_time VARCHAR(32) DEFAULT NULL,
	username VARCHAR(128),
	password VARCHAR(128),
	requestToken VARCHAR(128),
	requestSecret VARCHAR(128),
	email VARCHAR(128),
	phone VARCHAR(32),
	firmId INT,
	feeTier INT,
	requestStatus SMALLINT,
	lmm SMALLINT,
	senderCompId VARCHAR(128),
	targetCompId VARCHAR(128),
	registeredIP VARCHAR(32),
 	lastIP VARCHAR(32),
 	walletRequestToken VARCHAR(128),
	walletRequestSecret VARCHAR(128),
	verification INT,
 referral_code VARCHAR(128), 
 referred_by_code VARCHAR(128),
 use2auth SMALLINT,
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
 requestCount INT,
 orderCount INT,
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
 type VARCHAR(64),
  registerCode VARCHAR(128) DEFAULT NULL,
 lastWithdrawCode VARCHAR(128) DEFAULT NULL,
 useCodeValidaton INT DEFAULT 1
 );
   CREATE INDEX USER_STATE1 ON USER_STATE (email); 
 CREATE INDEX USER_STATE2 ON USER_STATE (requestToken);

 create table TRADE_HISTORY_LOG (
  	id BIGSERIAL PRIMARY KEY NOT NULL,
  	timeMillis VARCHAR(18),
  	securityId INT,
  	price BIGINT,
  	quantity BIGINT,
  	side CHAR(3)
 );
 

   create table WITHDRAW_REQUEST (
  	id BIGSERIAL PRIMARY KEY NOT NULL,
  	sequence_number BIGINT,
  	insert_time VARCHAR(32) DEFAULT NULL,
  	address VARCHAR(64),
  	updateType SMALLINT,
  	userId BIGINT,
  	assetId INT,
  	symbol VARCHAR(16),
  	balance_change BIGINT,
  	balance_change_scale INT,
  	balance BIGINT,
  	balance_scale INT,
  	confirms INT,
  	source VARCHAR(256),
  	updateBy VARCHAR(64),
  	signature VARCHAR(256),
  	status INT,
  	ip VARCHAR(32)
 );
  CREATE INDEX WITHDRAW_REQUEST1 ON WITHDRAW_REQUEST (userId); 
CREATE INDEX WITHDRAW_REQUEST2 ON WITHDRAW_REQUEST (assetId); 
CREATE INDEX WITHDRAW_REQUEST3 ON WITHDRAW_REQUEST (status);

 
  create table ADDRESS_STATE_LOG (
  	id BIGSERIAL PRIMARY KEY NOT NULL,
  	sequence_number BIGINT,
  	insert_time VARCHAR(32) DEFAULT NULL,
  	address VARCHAR(64),
  	updateType SMALLINT,
  	userId BIGINT,
  	assetId INT,
  	symbol VARCHAR(16),
  	balance_change BIGINT,
  	balance_change_scale INT,
  	balance BIGINT,
  	balance_scale INT,
  	confirms INT,
  	source VARCHAR(256),
  	updateBy VARCHAR(64),
  	signature VARCHAR(256)
 );
 
 create table ADDRESS_STATE (
  	id BIGSERIAL PRIMARY KEY NOT NULL,
  	sequence_number BIGINT,
  	insert_time VARCHAR(32) DEFAULT NULL,
  	address VARCHAR(64),
  	updateType SMALLINT,
  	userId BIGINT,
  	assetId INT,
  	symbol VARCHAR(16),
  	balance BIGINT,
  	balance_scale INT,
  	confirms INT,
  	source VARCHAR(256),
  	active SMALLINT,
  	updateBy VARCHAR(64),
  	signature VARCHAR(256),
  	status INT
 );
 CREATE INDEX ADDRESS_STATE1 ON ADDRESS_STATE (userId); 
CREATE INDEX ADDRESS_STATE2 ON ADDRESS_STATE (assetId); 
CREATE INDEX ADDRESS_STATE3 ON ADDRESS_STATE (active);




 create table ADDRESSES (
  	id BIGSERIAL PRIMARY KEY NOT NULL,
  	address VARCHAR(64),
  	userId BIGINT,
  	assetId INT,
  	symbol VARCHAR(16),
  	signature VARCHAR(256)
 );	
  	
  create table UPLOAD_FILE (
  	id BIGSERIAL PRIMARY KEY NOT NULL,
  	userId BIGINT,
  	file_key VARCHAR(128),
  	filename VARCHAR(128),
  	content_type VARCHAR(32),
  	proxy_location VARCHAR(128),  	
  	created TIMESTAMP default now()
 );
CREATE INDEX UPLOAD_FILE1 ON UPLOAD_FILE (file_key); 

 create table GEO_IP (
  	id BIGSERIAL PRIMARY KEY NOT NULL,
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
  	status INT,
  	created TIMESTAMP default now()
 );
  CREATE INDEX GEO_IP1 ON GEO_IP (ip); 
CREATE INDEX GEO_IP2 ON GEO_IP (countryName); 
 CREATE INDEX GEO_IP3 ON GEO_IP (status); 

     

  create table CHAIN_TRANSACTION_LOG (
  	id BIGSERIAL PRIMARY KEY NOT NULL,
  	userId BIGINT,
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
  	status INT,
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
  	note VARCHAR(256)
 );
  CREATE INDEX CHAIN_TRANSACTION_LOG1 ON CHAIN_TRANSACTION_LOG (userId); 
CREATE INDEX CHAIN_TRANSACTION_LOG2 ON CHAIN_TRANSACTION_LOG (instrumentId); 
 CREATE INDEX CHAIN_TRANSACTION_LOG3 ON CHAIN_TRANSACTION_LOG (address); 

 
 
  create table FUND (
  	id BIGSERIAL PRIMARY KEY NOT NULL,
  	user_id BIGINT,
  	user_manager_id BIGINT,
  	title VARCHAR(128),
  	description VARCHAR(2048),
  	keywords VARCHAR(2048),
  	image_url VARCHAR(256),
  	overview VARCHAR(2048),
  	manager_description VARCHAR(2048),
  	manager_fee double precision,
  	solfini_fee double precision,
  	manager_stake double precision,
  	created TIMESTAMP default now(),
  	last_updated TIMESTAMP default now(),
  	volatility double precision,
  	target_return double precision,
  	target_interest double precision,
  	nav double precision,
  	outstanding double precision,
  	inception_return double precision,
  	d1_return double precision,
  	d30_return double precision,
  	holding1 INT,
  	holding1_percent double precision,
  	holding1_name VARCHAR(64),
    holding2 INT,
  	holding2_percent double precision,
  	holding2_name VARCHAR(64),
  	holding3 INT,
  	holding3_percent double precision,
  	holding3_name VARCHAR(64),
  	holding4 INT,
  	holding4_percent double precision,
  	holding4_name VARCHAR(64),
  	holding5 INT,
  	holding5_percent double precision,
  	holding5_name VARCHAR(64),
  	status INT,
  	strategy VARCHAR(256),
  	yr1Return double precision,
  	valuation double precision,
  	divYield double precision,
  	benchmark double precision,
  	account_id BIGINT
 );
 
   CREATE INDEX FUND1 ON FUND (user_id); 
   CREATE INDEX FUND2 ON FUND (user_manager_id); 
   CREATE INDEX FUND3 ON FUND (holding1); 
   

INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (1,1,'20180507-00:09:58.756',1,1,'USDT','USDT',0,0,0,2,2,2,11,0,100,100,1.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (2,2,'20180507-00:09:58.756',1,2,'ETH','ETH',0,0,0,2,6,2,11,0,100,100,205.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (3,3,'20180507-00:09:58.756',1,3,'BTC','BTC',0,0,0,2,6,2,11,0,100,100,6500.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (4,4,'20180507-00:09:58.756',1,4,'ETH/USDT','ETH/USDT',1,2,1,2,6,2,11,0,100,100,235.0,0,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (5,5,'20180507-00:09:58.756',1,5,'BTC/USDT','BTC/USDT',1,3,1,2,6,2,11,0,100,100,6700.0,0,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (6,6,'20180507-00:09:58.756',1,6,'BTCTEST','BTCTEST',0,0,0,2,6,2,11,0,100,100,6700.0,0,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (7,7,'20180507-00:09:58.756',1,7,'ETHTEST','ETHTEST',0,0,0,2,6,2,11,0,100,100,235.0,0,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (8,8,'20180507-00:09:58.756',1,3,'BNB','BNB',0,0,0,2,6,2,11,0,100,100,15.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (20,9,'20180507-00:09:58.756',1,9,'ETH/USDT[F]','ETH/USDT[F]',1,2,1,2,6,2,12,1,10,20,150.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (21,10,'20180507-00:09:58.756',1,10,'LTC','LTC',0,0,0,2,6,2,11,0,100,100,1200.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (22,11,'20180507-00:09:58.756',1,11,'GLD/USDT[F]','GLD/USDT[F]',1,3,1,2,6,2,12,1,10,20,1200.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (23,12,'20180507-00:09:58.756',1,12,'BTC/USDT[F]','BTC/USDT[F]',1,3,1,2,6,2,12,1,10,20,7000.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (24,13,'20180507-00:09:58.756',1,13,'SLV/USDT[F]','SLV/USDT[F]',1,3,1,2,6,2,12,1,10,20,15.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (25,14,'20180507-00:09:58.756',1,14,'SPY/USDT[F]','SPY/USDT[F]',1,3,1,2,6,2,12,1,10,20,280.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (26,15,'20180507-00:09:58.756',1,15,'ETH/BTC','ETH/BTC',1,2,3,6,6,2,11,0,100,100,100.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (27,16,'20180507-00:09:58.756',1,16,'LTC/BTC','LTC/BTC',1,10,3,6,6,2,11,0,100,100,100.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (28,17,'20180507-00:09:58.756',1,17,'QQQ/USDT[F]','QQQ/USDT[F]',1,3,1,2,6,2,12,1,10,20,280.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (29,18,'20180507-00:09:58.756',1,18,'AAPL/USDT[F]','AAPL/USDT[F]',1,3,1,2,6,2,12,1,10,20,280.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (30,19,'20180507-00:09:58.756',1,19,'GOOG/USDT[F]','GOOG/USDT[F]',1,3,1,2,6,2,12,1,10,20,280.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (31,20,'20180507-00:09:58.756',1,20,'FB/USDT[F]','FB/USDT[F]',1,3,1,2,6,2,12,1,10,20,280.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (32,21,'20180507-00:09:58.756',1,21,'USO/USDT[F]','USO/USDT[F]',1,3,1,2,6,2,12,1,10,20,280.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (33,22,'20180507-00:09:58.756',1,22,'EWJ/USDT[F]','EWJ/USDT[F]',1,3,1,2,6,2,12,1,10,20,280.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (34,23,'20180507-00:09:58.756',1,23,'MCHI/USDT[F]','MCHI/USDT[F]',1,3,1,2,6,2,12,1,10,20,280.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (35,24,'20180507-00:09:58.756',1,24,'LMM','LMM',0,0,0,2,6,2,11,0,100,100,1000.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (36,25,'20180507-00:09:58.756',1,25,'LMM/USDT','LMM/USDT',1,24,1,2,6,2,11,0,100,100,1000.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (37,26,'20180507-00:09:58.756',1,26,'LMM/BTC','LMM/BTC',1,24,3,2,6,2,11,0,100,100,20.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (38,27,'20180507-00:09:58.756',1,27,'LMM/ETH','LMM/ETH',1,24,2,2,6,2,11,0,100,100,20.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (39,28,'20180507-00:09:58.756',1,28,'BNB/USDT[F]','BNB/USDT[F]',1,3,1,2,6,2,12,1,10,20,15.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (40,29,'20180507-00:09:58.756',1,29,'USDC','USDC',0,0,0,2,2,2,11,0,100,100,1.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (41,30,'20180507-00:09:58.756',1,30,'LVG','LVG',0,0,0,2,2,2,11,0,100,100,1.0,1,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (42,31,'20180507-00:09:58.756',1,5,'BTC/USDC','BTC/USDC',1,3,29,2,6,2,11,0,100,100,6700.0,0,null,null,null,null);
INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,daysfeedisactive,estimatedvolatility,estimatedvar) VALUES (43,32,'20180507-00:09:58.756',1,5,'LVG/USDC','LVG/USDC',1,30,29,2,6,2,11,0,100,100,6700.0,0,null,null,null,null);


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

  ALTER SEQUENCE USER_STATE_id_seq RESTART WITH 23;

INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181009-09:01:38.983','testtest','gg40M8i55QNeyeCgnwFmNg==','0kAQXruIMK0gySTW','b7LQAEvb36osORYb','testtest@test.com','test',0,0,0,0,'1000000023','',null,null,null,null,null,'odrCIIacfv5Axwc4','abcdef',0,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181011-06:53:12.489','onetwo','f1zQyeMb9rdsC8y1MZ1qLQ==','CTYxgnj3YqfOqAkU','7l9MAx8KDjEGc8h4','one@two.com','123456789',0,0,0,0,'1000000024','',null,null,null,null,null,'IAX7j08UzKfeCzYY',null,0,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181011-06:54:55.006','one','BJ06MZh849CnAdp2YYYfsw==','sZML64oz7v53Yvxp','j3twplr3j9q7t1Bo','one@two.com','12345678',0,0,0,0,'','',null,null,null,null,null,'L8sIFQw1jcw8vHa9',null,0,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181011-06:54:56.395','one','BJ06MZh849CnAdp2YYYfsw==','Bs0oRzU8dvjTcMnN','HlRMnPz29c3FRIVm','one@two.com','12345678',0,0,0,0,'','',null,null,null,null,null,'zVhZd5uIhgtSr9rD',null,0,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181011-06:55:02.945','one','BJ06MZh849CnAdp2YYYfsw==','AfvBtbYoIeQtR3tk','n36O3BPLk6oDZOMv','one@two.com','12345678',0,0,0,0,'','',null,null,null,null,null,'MYni5hyxrUL86Oca',null,0,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181011-06:55:03.313','one','BJ06MZh849CnAdp2YYYfsw==','UnRZtLqcgIuzVjSc','UrzbtyJNEjzsXbrL','one@two.com','12345678',0,0,0,0,'1000000028','',null,null,null,null,null,'mL78g6yjqzMUK4ok',null,0,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);


INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181011-12:01:36.163','test2','gg40M8i55QNeyeCgnwFmNg==','q2cTEILyAFfTtjN1','k7FDmrIIuEu7sa67','test2@gmail.com','12345',0,0,0,0,'1000000029','',null,null,null,null,null,'16S4CsjSUosK37cc',null,0,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181017-05:01:46.228','test55','5oyHEht1wamggJQgPpJMUg==','SV8xICOwHEkm1ETQ','XQFfegwIyjFLuAnS','test55@test.com','5555555',0,0,0,0,'1000000030','',null,null,null,null,null,'rIScl9G45CDO0cZq',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181017-05:04:21.228','test56','lgdbaJv7nV0bPVjgEs3pPw==','fO4dlnSQYDNP1bHl','XE9WKN8ngtRnYVi8','test@test.com','12312412',0,0,0,0,'1000000031','',null,null,null,null,null,'s4yqq79DNeDxoyYy',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181020-07:10:55.830','Milan','E5c7PgRiW8lX_GmFr-LVqQ==','3juHIKsbTmSauMhB','RlYwREAGjc6pikYM','milan@ashutec.com','test@123',0,0,0,0,'1000000032','',null,null,null,null,null,'uoJYlAA5Ga1nleoN',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181020-11:05:01.266','test3','gg40M8i55QNeyeCgnwFmNg==','Ubw4xHClw6axlXmh','dIVd6GPymnOR3Vha','test3@test.com','test',0,0,0,0,'1000000033','',null,null,null,null,null,'4JJ13nPMGg4ubbDz',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181022-08:57:49.390','test11','qjSZVyNF-nkf3p-acMJoNQ==','bXCpePwkMjDADWp4','9fCHCVx3bPU5HjoY','test11@gmail.com','123456789',0,0,0,0,'1000000034','',null,null,null,null,null,'1IvalCVSt4sSkvkR',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181022-10:00:12.220','test123','f1zQyeMb9rdsC8y1MZ1qLQ==','VCeUiobcWMDI2ZJa','sDqNM1d5AhbqONBe','test123@gmail.com','123456789',0,0,0,0,'1000000035','',null,null,null,null,null,'iuzPhQn3iuEvojov',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181026-06:33:04.034','r','gg40M8i55QNeyeCgnwFmNg==','xr8Qbe0NKYxnBAgj','lBnI8INIiD4UJ1cZ','s@s.com','12345678',0,0,0,0,'1000000036','','27.109.9.74','27.109.9.74','','',0,'','null',1,'PA4O7Z7RBTYRX3UB','https://chart.googleapis.com/chart?chs=200x200&chld=M%7C0&cht=qr&chl=otpauth%3A%2F%2Ftotp%2Fsolfini.com%3Ar%3Fsecret%3DPA4O7Z7RBTYRX3UB%26issuer%3Dsolfini.com','','r','','r','makarba','makrba','makrba','ahmedabad','gujarat','38050','India','2018-10-26','',0,0,'','','','','','','','','','','','','','','','','','','','','','','','',null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181102-04:32:10.554','test60','MNrEJ9VLtvPXWzJTEhrOgQ==','f7QePvksLBKWc1uI','y6WD1vVXTvU5dclf','test60@test.com','875858753',0,0,0,0,'1000000037','',null,null,null,null,null,'SLIbHB475qMVlKsI',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181102-04:41:30.725','test62','3OLo9cWmw6F9YdPSz6HLIw==','Z4wqvBlkYsosxigG','74mFuUDGiXg7s0l5','test62@test62.com','986986986',0,0,0,0,'1000000038','',null,null,null,null,null,'wOALTOEbrp4c8qnD','aaaaab',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181102-05:38:44.453','demo','gg40M8i55QNeyeCgnwFmNg==','7ITbxFbTp7pERCNr','COCgv9vhHdzbZ31n','demo@test.com','test',0,0,0,0,'1000000039','',null,null,null,null,null,'j8Owluia4GsJq6R3','aaaaab',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181102-10:46:40.721','demo1','gg40M8i55QNeyeCgnwFmNg==','I2zINSXPonfoyzDc','f6xLDShkscOgtWNM','demo1@test.com','123456',0,0,0,0,'1000000040','',null,null,null,null,null,'zWU2l5BGhmYI9zTV','aaaaab',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181104-03:37:13.012','test64','yPVhEDuAP5iqPxsHrJ4xJA==','tfvkz2bE6ZrXyF9m','panFDsNmtv4QuBSW','456456456','6462151487',0,0,0,0,'1000000041','',null,null,null,null,null,'vxqSAyJZBRHMMNZk','555556788',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181104-19:23:40.547','test65','7l9OhlYrVtI1-EvPdCVIcA==','bGBGpSBknSfMgxD0','oyeQSSClNWjNHK8C','test65@test65.com','45645456',0,0,0,0,'1000000042','',null,null,null,null,null,'18J6dLRurIs2g728',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181104-19:57:34.859','test66','iciWD8p1Fp3Oa3N5x5B-NA==','HyCgJVvxcZVteSjd','GXbaYWDANVuAD29Q','test66@test66.com','087987987',0,0,0,0,'1000000043','',null,null,null,null,null,'yiqD5AqdqCoTGQEN',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181105-04:09:14.222','manager_Fd1nIpo7bmBcbLaN@solfini.com','m6VlhWOCXnSRIlGexkVfQTfN0RURZ0rlCxJujJ-ANlA=','s26JzmRVn7AzxLrZ','MmBA7cQzqEly5IeG','manager_Fd1nIpo7bmBcbLaN@solfini.com','',0,0,0,0,'1000000044','',null,null,null,null,null,'c74fl7d4Zrwy0Q3g','',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,'fund',null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181105-05:43:11.696','test67','Vurxxsjw0dRSMoxwjnU18w==','DATv017qaWpaVIyl','92SeqZce0ngkJ71p','test67@test67.com','897987987',0,0,0,0,'1000000045','',null,null,null,null,null,'61yGyB8d7VGBEz7D','555556788',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181105-05:57:07.348','test68','wUwbIY3tDDJMZHgfm7pAAA==','1l32vxvjZHtcUu03','mE40ytwLoYZGe0ob','test68@test68.com','87987987987',0,0,0,0,'1000000046','','98.14.195.112','98.14.195.112',null,null,null,'ajHloBD4dpBje5LC',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181105-09:37:26.423','test5','cbM13Ma1t61a4al_A-9L6A==','n0JrdYgInRb9C5Zk','s5hvsY184gazSndh','test5@gmail.com','123456',0,0,0,0,'1000000047','','27.109.9.74','27.109.9.74',null,null,null,'dQYY3e91iu7Ll5zp',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181105-09:42:25.048','test4','unio_UGY4oalUiNXj308BA==','OV7E8w2kbm8RvZxd','CbDCPlyeJuy7GnLB','test4@gmail.com','123456',0,0,0,0,'1000000048','','27.109.9.74','27.109.9.74',null,null,null,'TqE50uoNTkbzaOcR',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181105-09:48:04.485','test6','CcT-sryx1YdncFkyBu4kBQ==','cTJYrdWVpl3BLSGz','FuLMxONmXa6Ha1dw','test6@gmail.com','123456',0,0,0,0,'1000000049','','27.109.9.74','27.109.9.74',null,null,null,'64byncWNfIf2BEvq',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181106-13:53:32.171','dizzymax','HfrLGf-xx98D5Sr0Fxfr6J9UIX3EMZbTamTokBzXk_Y=','PJRvYbFGWLip2YkI','GUW03HoFc0kJxJvu','dizzymax@gmail.com','NWH80pWAaf8QSfQ2mO',0,0,0,0,'1000000050','','84.50.177.92','84.50.177.92',null,null,null,'zilZDf2rfDKoBAJy',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181110-02:11:31.688','Riemannstein','AAICMtBDAtJ5qICb_owU6A==','OuqLNgMQTxEOtzRU','kzInP1d8EwMJqmvT','houyifeng1005@hotmail.com','+85268263649',0,0,0,0,'1000000051','','42.200.194.108','42.200.194.108',null,null,null,'89W4eq1OPzHZEnTo',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181110-04:36:07.035','hou','AAICMtBDAtJ5qICb_owU6A==','M2HzzACo6UrDUFAt','TryPqDsWUPwK7Mf2','houyifeng1005@gmail.com','68263649',0,0,0,0,'1000000052','','210.17.198.226','210.17.198.226',null,null,null,'l24i3Vc9aM77kufa',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181113-10:53:57.752','deepti','QWDuhEoSBc7a4ls2nmEOWw==','p35d6qqYmS2VouDW','Ib5bZ7g9BTndVG8G','deepti@ashutec.com','45464646464644',0,0,0,0,'1000000053','','27.109.9.74','27.109.9.74',null,null,null,'wtNn30xwYQetQYJ7',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181114-10:53:14.892','123123','B1ow-e_o3cdj_17L8FQwbg==','oUojotrocj0LDboA','Rq1BCjsot3wZ4Hex','123123@123.123','123123123',0,0,0,0,'1000000054','','27.109.9.74','27.109.9.74',null,null,null,'oofBsTDXelWxjvrQ',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181119-10:09:01.589','123test','gg40M8i55QNeyeCgnwFmNg==','XzyYRUW2ypTtabzR','6KFmHfIa7xI38RzT','test@test.123','123456',0,0,0,0,'1000000055','','27.109.9.74','27.109.9.74',null,null,null,'nileRKVOMwlgGSob',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181120-06:01:52.598','twor','gg40M8i55QNeyeCgnwFmNg==','ZmSkl1xuxJ8fTmEX','VM241BnU6AVXJnpc','TWO@ONE.com','123456',0,0,0,0,'1000000056','','27.109.9.74','27.109.9.74',null,null,null,'pzaJnpmnVtgS5BVy',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181121-10:38:20.111','tyu','gg40M8i55QNeyeCgnwFmNg==','DvxDw6MWul6s8y9i','xORsBcmp8eSS1VIR','sdf@erwte.io','123456',0,0,0,0,'1000000057','','27.109.9.74','27.109.9.74',null,null,null,'kQLWzN8kzHlQ2XzO',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181121-10:40:24.046','ert','n-Yez5ZZY8ph469aVgMZ_A==','eAwMoex5xsWdKbW1','JjY89ETk2au395my','wer@tert.tr','3453453',0,0,0,0,'1000000058','','27.109.9.74','27.109.9.74',null,null,null,'0BELeOcNkVcFddzy',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181121-10:42:47.710','two','gg40M8i55QNeyeCgnwFmNg==','QXoICsb1evs3oR1q','6YUr9NZ3rY0enHSQ','two@two.two','123456',0,0,0,0,'1000000059','','27.109.9.74','27.109.9.74',null,null,null,'E4ZbHVf8J2FsqAnb',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181121-12:38:05.142','test124','gg40M8i55QNeyeCgnwFmNg==','TdejGV4ISaGmHmLj','CxQCVjjr2nlBpNdC','test124@test.ted','123456',0,0,0,0,'1000000060','','27.109.9.74','27.109.9.74',null,null,null,'IsG0Fz5Yv83DGqcA',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181126-09:28:48.040','testy','gg40M8i55QNeyeCgnwFmNg==','r3rgNdQF3PertvTT','GyU0qygahAWLSO4K','testy.12@gmail.com','123123',0,0,0,0,'','','27.109.9.74','27.109.9.74',null,null,null,'CD9bXnfolMborZ7l',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181126-09:29:46.754','testy','gg40M8i55QNeyeCgnwFmNg==','5nkaBJqqCfEj07uA','2HXFD6yR1mRQWE0F','testy@gmail.com','123123',0,0,0,0,'1000000062','','27.109.9.74','27.109.9.74',null,null,null,'ZB6kDEM6Y7pCw1fp',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181126-09:33:18.627','testy12','gg40M8i55QNeyeCgnwFmNg==','nhkGoKkIDnLRyAD2','GiexRBtARIF5xwiz','test@testy.com','2131123',0,0,0,0,'1000000063','','27.109.9.74','27.109.9.74',null,null,null,'2mADUUsBtW4rCXGi',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181127-11:51:33.476','testing123','5CMAvxUOOBe9gdm57ByJSQ==','JXnZlnwgYq5JXjoE','J5PXm7JemOkrJZJk','testing123@gmail.com','1234567897',0,0,0,0,'','','27.109.9.74','27.109.9.74',null,null,null,'ieXwgR9BaMDfLiTP',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181127-11:51:29.008','testing123','5CMAvxUOOBe9gdm57ByJSQ==','sZXOobYR1Abv4ntn','bUsvg8qHaipPCojY','testing123@gmail.com','1234567897',0,0,0,0,'','','27.109.9.74','27.109.9.74',null,null,null,'SO5uE8xPjXZHyDcu',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181127-11:51:26.780','testing123','5CMAvxUOOBe9gdm57ByJSQ==','Kj86JNjjkBZaYOE8','waPzWn0vazTyhviB','testing123@gmail.com','1234567897',0,0,0,0,'','','27.109.9.74','27.109.9.74',null,null,null,'tGlbul466XdMLgqS',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181127-11:51:30.763','testing123','5CMAvxUOOBe9gdm57ByJSQ==','q9X5CM3eikANgQIx','IyqErWNCf3N0hzpk','testing123@gmail.com','1234567897',0,0,0,0,'','','27.109.9.74','27.109.9.74',null,null,null,'Mv46PNjdZxVJWqOv',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181127-11:51:20.538','testing123','5CMAvxUOOBe9gdm57ByJSQ==','1JZw5roU9HwcFyD8','G36ojVxaH0ltkDvA','testing123@gmail.com','1234567897',0,0,0,0,'1000000068','','27.109.9.74','27.109.9.74',null,null,null,'gnNRFo9nL5oEnlH7',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181227-17:26:05.086','test77','CBvokePV7LbFBThdQn-mUQ==','eb4WLKxXijzO1g4X','9BSal2KnT2jpvLRv','test77@test.com','4654654115',0,0,0,0,'','','98.14.195.112','98.14.195.112',null,null,null,'IKa2p8YeeKLHPCp7','555556788',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20181227-17:24:56.884','test77','CBvokePV7LbFBThdQn-mUQ==','Rt9vuiOoT9xt1vno','4xeB2SG3dHFjDtma','test77@test.com','4654654115',0,0,0,0,'1000000070','','98.14.195.112','98.14.195.112',null,null,null,'K9cyRENs1aNxSz3t','555556788',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20190104-20:34:07.967','mikmik','kOPerr5ZbjOwh74vWSLeMg==','xTejDb36ZATjedCJ','pZ9Hkuk3kriT4TU8','franck@fxclr.com','+353868295811',0,0,0,0,'1000000071','','89.134.145.17','89.134.145.17',null,null,null,'h252lCZXYaMXinDo',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20190116-20:44:50.332','test25','gg40M8i55QNeyeCgnwFmNg==','1spHl4S7AOggynkT','NovPhUV8s6QbeeSd','steve@bitsian.io','212-555-5555',0,0,0,0,'1000000072','','98.14.195.112','98.14.195.112',null,null,null,'eL6qylhudHAuOT6p','555556788',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20190116-20:48:47.455','test26','zeYDH64kqFVSI5kQK2l02g==','BDIQm9RWnaIpYdEo','8G7cSP5veNsO4zRL','ifgjfgjfgj','2122285600',0,0,0,0,'1000000073','','98.14.195.112','98.14.195.112',null,null,null,'HPxQvxj3Qf5YHChB',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20190117-05:54:48.177','test28','P-QiwlhTLo-hPl6vTG05Cg==','ZydopTkS4gAAidet','3fub0iOjN6ii5x6v','test28@test28.com','223622226',0,0,0,0,'1000000074','','98.14.195.112','98.14.195.112',null,null,null,'I3ZbmTokoFiR0Acs',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20190117-06:14:37.456','test31','su63M1nINtZ6ipmorbAS8w==','V5e2IL4Q3gLdAXvp','EjUxNa9Td8NMSPyI','test31@test31.com','2352352352',0,0,0,0,'1000000075','','98.14.195.112','98.14.195.112',null,null,null,'HMX1D8j7QjZgIX41',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20190117-06:39:13.935','test32','iZz0pwDwpPMwG8ZKvwvaEg==','ZJzkzrYnhGQdzBqt','RGVNuMEsXZKet1Od','test32@test32.com','4345345345',0,0,0,0,'1000000076','','98.14.195.112','98.14.195.112',null,null,null,'UVznKEIB549uxBI4',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20190117-07:10:40.450','test33','aZxuYyflaRWD9-CBlKegJg==','PlvkzyjUU8oWxah3','AOxznhIdiVKGB5Ly','test33@test.com','23234234',0,1,0,0,'1000000077','','98.14.195.112','98.14.195.112',null,null,null,'bzTiokN2VO2vQlFz',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20190118-21:18:05.425','test34','niY-cQmp6dzQ6LpCZRsSkg==','A2dEecIGXpOGk1gv','P5VzDtk4mohA75Zg','raakhee@gmail.com','1234567891',0,1,0,0,'1000000078','','152.179.55.78','152.179.55.78',null,null,null,'0gIl5AgsFuCtitkq',null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,1);
INSERT INTO USER_STATE (sequence_number,insert_time,username,password,requestToken,requestSecret,email,phone,firmId,feeTier,requestStatus,lmm,senderCompId,targetCompId,registeredIP,lastIP,walletRequestToken,walletRequestSecret,verification,referral_code,referred_by_code,use2auth,authToken,authUrl,companyname,firstname,middlename,lastname,address1,address2,address3,city,state,zip,country,birthdate,taxid,requestCount,orderCount,linkedin,facebook,skype,photo_url,icon_url,id_url1,id_url2,id_url3,id_url4,id_url5,security_question1,security_answer1,security_question2,security_answer2,security_question3,security_answer3,security_question4,security_answer4,security_question5,security_answer5,emergency_contact_name,emergency_contact_email,emergency_contact_phone,emergency_btc_address,type,registerCode,lastWithdrawCode,useCodeValidaton) VALUES (0,'20190121-06:20:50.251','test36','6ZAE6EiBQgxSDRRzdBHKQQ==','ehljN9MenTy4bz1g','z6FkkTrOoa4mkaUc','cosdfsdfdsf','234234234',0,1,0,0,'1000000079','','98.14.195.112','98.14.195.112','','',1,'qH8cw7Dingwnwlln','',0,'','','','','','','','','','','','','','','',0,0,'','','','','','','','','','','','','','','','','','','','','','','','',null,'dbmzg7K3TQXkrlcfmaXLlZ8g','',1);
  

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




update SECURITY_DEFINITION_LOG set status=1;
update SECURITY_DEFINITION_LOG set status=0 where securityId in(4,5,6,7,1);

--INSERT INTO SECURITY_DEFINITION_LOG (id,sequence_number,insert_time,updateType,securityId,symbol,name,assetType,quotedId,baseId,priceScale,quantityScale,orderBookStrategy,preOrderCheckStrategy,settleType,maintMarginPercent,requiredMarginPercent,usdMark,status) VALUES (20,9,'20180507-00:09:58.756',1,9,'ETH/USD[F]','ETH/USD[F]',1,2,1,2,6,2,12,1,10,20,150.0,1);

--------
INSERT INTO FUND (id,user_id,user_manager_id,title,description,keywords,image_url,overview,manager_description,manager_fee,solfini_fee,manager_stake,created,last_updated,volatility,target_return,target_interest,nav,outstanding,inception_return,d1_return,d30_return,holding1,holding1_percent,holding1_name,holding2,holding2_percent,holding2_name,holding3,holding3_percent,holding3_name,holding4,holding4_percent,holding4_name,holding5,holding5_percent,holding5_name,status,strategy,yr1Return,valuation,divYield,benchmark,account_id) VALUES (1,18,18,'test fund demo1','test fund demo test fund demo test fund demo test fund demo 123 test fund demo test fund demo test fund demo test fund demo 123 test fund demo test fund demo test fund demo test fund demo 123','test','https://d23nqp6cqodith.cloudfront.net/img/motif/478','test','test abc',0.0,0.0,0.0,{ts '2018-09-19 03:07:42'},{ts '2018-11-14 14:17:59'},10.0,0.0,0.0,0.0,100.0,0.0,0.0,0.0,10,10.0,'test1',20,20.0,'test2',30,30.0,'test3',40,40.0,'test4',50,50.0,'test5',0,'featured',0.0,0.0,0.0,0.0,44);
INSERT INTO FUND (id,user_id,user_manager_id,title,description,keywords,image_url,overview,manager_description,manager_fee,solfini_fee,manager_stake,created,last_updated,volatility,target_return,target_interest,nav,outstanding,inception_return,d1_return,d30_return,holding1,holding1_percent,holding1_name,holding2,holding2_percent,holding2_name,holding3,holding3_percent,holding3_name,holding4,holding4_percent,holding4_name,holding5,holding5_percent,holding5_name,status,strategy,yr1Return,valuation,divYield,benchmark,account_id) VALUES (2,18,18,'testFund2','Test Desciption','fund1','https://d23nqp6cqodith.cloudfront.net/img/motif/525','demo2','abc',0.0,0.0,0.0,{ts '2018-10-12 15:24:32'},{ts '2018-11-14 14:18:09'},1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,11,11.0,'holding1',0,0.0,'',31,31.0,'holding3',0,0.0,'',0,0.0,'',0,'featured',0.0,0.0,0.0,0.0,44);
INSERT INTO FUND (id,user_id,user_manager_id,title,description,keywords,image_url,overview,manager_description,manager_fee,solfini_fee,manager_stake,created,last_updated,volatility,target_return,target_interest,nav,outstanding,inception_return,d1_return,d30_return,holding1,holding1_percent,holding1_name,holding2,holding2_percent,holding2_name,holding3,holding3_percent,holding3_name,holding4,holding4_percent,holding4_name,holding5,holding5_percent,holding5_name,status,strategy,yr1Return,valuation,divYield,benchmark,account_id) VALUES (3,18,18,'qwerty3','qwerty qwerty qwerty qwerty','asa','https://d23nqp6cqodith.cloudfront.net/img/motif/569497','qwertyu ','qqwerty',12.0,12.0,1212.0,{ts '2018-10-12 17:30:42'},{ts '2018-11-14 14:18:18'},1.0,12.0,0.0,12.0,12.0,12.0,12.0,12.0,12,12.0,'qw',12,12.0,'qw',12,12.0,'qw',12,12.0,'qw',12,12.0,'qw',12,'crypto',0.0,0.0,0.0,0.0,44);
INSERT INTO FUND (id,user_id,user_manager_id,title,description,keywords,image_url,overview,manager_description,manager_fee,solfini_fee,manager_stake,created,last_updated,volatility,target_return,target_interest,nav,outstanding,inception_return,d1_return,d30_return,holding1,holding1_percent,holding1_name,holding2,holding2_percent,holding2_name,holding3,holding3_percent,holding3_name,holding4,holding4_percent,holding4_name,holding5,holding5_percent,holding5_name,status,strategy,yr1Return,valuation,divYield,benchmark,account_id) VALUES (4,18,18,'mobile-internet-tsunami4','lorem ipcdd dfso;','test','https://d23nqp6cqodith.cloudfront.net/img/motif/456','test','test',0.0,0.0,0.0,{ts '2018-10-15 08:16:01'},{ts '2018-11-14 14:18:25'},1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0,0.0,'q',0,0.0,'q',0,0.0,'q',0,0.0,'q',0,0.0,'q',0,'focused',0.0,0.0,0.0,0.0,44);
INSERT INTO FUND (id,user_id,user_manager_id,title,description,keywords,image_url,overview,manager_description,manager_fee,solfini_fee,manager_stake,created,last_updated,volatility,target_return,target_interest,nav,outstanding,inception_return,d1_return,d30_return,holding1,holding1_percent,holding1_name,holding2,holding2_percent,holding2_name,holding3,holding3_percent,holding3_name,holding4,holding4_percent,holding4_name,holding5,holding5_percent,holding5_name,status,strategy,yr1Return,valuation,divYield,benchmark,account_id) VALUES (5,18,18,'Highcharts Demo5','aqcx','d','','','',0.0,0.0,0.0,{ts '2018-10-15 08:30:08'},{ts '2018-11-14 14:18:36'},0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',0,'stocks',0.0,0.0,0.0,0.0,44);
INSERT INTO FUND (id,user_id,user_manager_id,title,description,keywords,image_url,overview,manager_description,manager_fee,solfini_fee,manager_stake,created,last_updated,volatility,target_return,target_interest,nav,outstanding,inception_return,d1_return,d30_return,holding1,holding1_percent,holding1_name,holding2,holding2_percent,holding2_name,holding3,holding3_percent,holding3_name,holding4,holding4_percent,holding4_name,holding5,holding5_percent,holding5_name,status,strategy,yr1Return,valuation,divYield,benchmark,account_id) VALUES (6,18,18,'s6','aa','s','','','',0.0,0.0,0.0,{ts '2018-10-15 08:45:00'},{ts '2018-11-14 14:17:43'},0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',0,'stocks',0.0,0.0,0.0,0.0,44);
INSERT INTO FUND (id,user_id,user_manager_id,title,description,keywords,image_url,overview,manager_description,manager_fee,solfini_fee,manager_stake,created,last_updated,volatility,target_return,target_interest,nav,outstanding,inception_return,d1_return,d30_return,holding1,holding1_percent,holding1_name,holding2,holding2_percent,holding2_name,holding3,holding3_percent,holding3_name,holding4,holding4_percent,holding4_name,holding5,holding5_percent,holding5_name,status,strategy,yr1Return,valuation,divYield,benchmark,account_id) VALUES (7,18,18,'sdf7','aa','sdf','','','',0.0,0.0,0.0,{ts '2018-10-15 08:46:44'},{ts '2018-11-14 14:17:51'},0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',0,'active managed',0.0,0.0,0.0,0.0,44);
INSERT INTO FUND (id,user_id,user_manager_id,title,description,keywords,image_url,overview,manager_description,manager_fee,solfini_fee,manager_stake,created,last_updated,volatility,target_return,target_interest,nav,outstanding,inception_return,d1_return,d30_return,holding1,holding1_percent,holding1_name,holding2,holding2_percent,holding2_name,holding3,holding3_percent,holding3_name,holding4,holding4_percent,holding4_name,holding5,holding5_percent,holding5_name,status,strategy,yr1Return,valuation,divYield,benchmark,account_id) VALUES (8,18,18,'sdf8','aa','sdf','','','',0.0,0.0,0.0,{ts '2018-10-15 08:47:01'},{ts '2018-11-14 14:18:48'},0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',0,'fixed income',0.0,0.0,0.0,0.0,44);
INSERT INTO FUND (id,user_id,user_manager_id,title,description,keywords,image_url,overview,manager_description,manager_fee,solfini_fee,manager_stake,created,last_updated,volatility,target_return,target_interest,nav,outstanding,inception_return,d1_return,d30_return,holding1,holding1_percent,holding1_name,holding2,holding2_percent,holding2_name,holding3,holding3_percent,holding3_name,holding4,holding4_percent,holding4_name,holding5,holding5_percent,holding5_name,status,strategy,yr1Return,valuation,divYield,benchmark,account_id) VALUES (9,18,18,'df9','as','df','','','',0.0,0.0,0.0,{ts '2018-10-15 09:04:27'},{ts '2018-11-14 14:18:58'},0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',0,'fixed income',0.0,0.0,0.0,0.0,44);
INSERT INTO FUND (id,user_id,user_manager_id,title,description,keywords,image_url,overview,manager_description,manager_fee,solfini_fee,manager_stake,created,last_updated,volatility,target_return,target_interest,nav,outstanding,inception_return,d1_return,d30_return,holding1,holding1_percent,holding1_name,holding2,holding2_percent,holding2_name,holding3,holding3_percent,holding3_name,holding4,holding4_percent,holding4_name,holding5,holding5_percent,holding5_name,status,strategy,yr1Return,valuation,divYield,benchmark,account_id) VALUES (10,18,18,'d10','qwerty','d','','','',0.0,0.0,0.0,{ts '2018-10-15 09:07:05'},{ts '2018-11-14 14:19:20'},0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',0,'active managed',0.0,0.0,0.0,0.0,44);
INSERT INTO FUND (id,user_id,user_manager_id,title,description,keywords,image_url,overview,manager_description,manager_fee,solfini_fee,manager_stake,created,last_updated,volatility,target_return,target_interest,nav,outstanding,inception_return,d1_return,d30_return,holding1,holding1_percent,holding1_name,holding2,holding2_percent,holding2_name,holding3,holding3_percent,holding3_name,holding4,holding4_percent,holding4_name,holding5,holding5_percent,holding5_name,status,strategy,yr1Return,valuation,divYield,benchmark,account_id) VALUES (11,18,18,'d11','rtyuiop','d','','','',0.0,0.0,0.0,{ts '2018-10-15 09:10:25'},{ts '2018-11-14 14:19:29'},0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',0,'active managed',0.0,0.0,0.0,0.0,44);
INSERT INTO FUND (id,user_id,user_manager_id,title,description,keywords,image_url,overview,manager_description,manager_fee,solfini_fee,manager_stake,created,last_updated,volatility,target_return,target_interest,nav,outstanding,inception_return,d1_return,d30_return,holding1,holding1_percent,holding1_name,holding2,holding2_percent,holding2_name,holding3,holding3_percent,holding3_name,holding4,holding4_percent,holding4_name,holding5,holding5_percent,holding5_name,status,strategy,yr1Return,valuation,divYield,benchmark,account_id) VALUES (12,18,18,'f12','zaqwertyu','f','','','',0.0,0.0,0.0,{ts '2018-10-15 09:11:04'},{ts '2018-11-14 14:19:39'},0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',0,'crypto',0.0,0.0,0.0,0.0,44);
INSERT INTO FUND (id,user_id,user_manager_id,title,description,keywords,image_url,overview,manager_description,manager_fee,solfini_fee,manager_stake,created,last_updated,volatility,target_return,target_interest,nav,outstanding,inception_return,d1_return,d30_return,holding1,holding1_percent,holding1_name,holding2,holding2_percent,holding2_name,holding3,holding3_percent,holding3_name,holding4,holding4_percent,holding4_name,holding5,holding5_percent,holding5_name,status,strategy,yr1Return,valuation,divYield,benchmark,account_id) VALUES (13,18,18,'d13','qwqwwq','d','','','',0.0,0.0,0.0,{ts '2018-10-15 09:11:38'},{ts '2018-11-14 14:19:49'},0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',0,'focused',0.0,0.0,0.0,0.0,44);
INSERT INTO FUND (id,user_id,user_manager_id,title,description,keywords,image_url,overview,manager_description,manager_fee,solfini_fee,manager_stake,created,last_updated,volatility,target_return,target_interest,nav,outstanding,inception_return,d1_return,d30_return,holding1,holding1_percent,holding1_name,holding2,holding2_percent,holding2_name,holding3,holding3_percent,holding3_name,holding4,holding4_percent,holding4_name,holding5,holding5_percent,holding5_name,status,strategy,yr1Return,valuation,divYield,benchmark,account_id) VALUES (14,22,22,'s','ss','s','','ss','s',1.0,1.0,1.0,{ts '2018-10-15 10:43:09'},{ts '2018-11-14 14:21:50'},1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1,1.0,'s',1,1.0,'s',1,1.0,'s',1,1.0,'s',1,1.0,'s',1,'stocks',0.0,0.0,0.0,0.0,44);
INSERT INTO FUND (id,user_id,user_manager_id,title,description,keywords,image_url,overview,manager_description,manager_fee,solfini_fee,manager_stake,created,last_updated,volatility,target_return,target_interest,nav,outstanding,inception_return,d1_return,d30_return,holding1,holding1_percent,holding1_name,holding2,holding2_percent,holding2_name,holding3,holding3_percent,holding3_name,holding4,holding4_percent,holding4_name,holding5,holding5_percent,holding5_name,status,strategy,yr1Return,valuation,divYield,benchmark,account_id) VALUES (15,18,18,'test13','qweasdc test12','test23','','test13','test13',0.0,0.0,11.0,{ts '2018-10-15 12:06:25'},{ts '2018-11-14 07:41:59'},0.0,12.0,12.0,0.0,0.0,0.0,0.0,0.0,0,0.0,'q',2,1.0,'q',0,0.0,'',0,0.0,'q',0,0.0,'q',12,'fixed income',0.0,0.0,0.0,0.0,44);
INSERT INTO FUND (id,user_id,user_manager_id,title,description,keywords,image_url,overview,manager_description,manager_fee,solfini_fee,manager_stake,created,last_updated,volatility,target_return,target_interest,nav,outstanding,inception_return,d1_return,d30_return,holding1,holding1_percent,holding1_name,holding2,holding2_percent,holding2_name,holding3,holding3_percent,holding3_name,holding4,holding4_percent,holding4_name,holding5,holding5_percent,holding5_name,status,strategy,yr1Return,valuation,divYield,benchmark,account_id) VALUES (16,18,18,'add fund14','loren ipsum','loren','','loren','loren ipsumloren ipsumloren ipsumloren ipsum',0.0,0.0,2.0,{ts '2018-10-22 09:54:08'},{ts '2018-11-14 14:20:04'},1.0,2.0,2.0,0.0,0.0,0.0,0.0,0.0,2,2.0,'2',2,2.0,'2',2,2.0,'2',2,2.0,'2',2,2.0,'2',2,'structured product',0.0,0.0,0.0,0.0,44);
INSERT INTO FUND (id,user_id,user_manager_id,title,description,keywords,image_url,overview,manager_description,manager_fee,solfini_fee,manager_stake,created,last_updated,volatility,target_return,target_interest,nav,outstanding,inception_return,d1_return,d30_return,holding1,holding1_percent,holding1_name,holding2,holding2_percent,holding2_name,holding3,holding3_percent,holding3_name,holding4,holding4_percent,holding4_name,holding5,holding5_percent,holding5_name,status,strategy,yr1Return,valuation,divYield,benchmark,account_id) VALUES (17,18,18,'ad fund demo15','loren ipsum ','loren ipsum ','','loren ipsum  loren ipsum  loren ipsum loren ipsum loren ipsum loren ipsum ','loren ipsum ',0.0,0.0,1.0,{ts '2018-10-22 10:03:52'},{ts '2018-11-14 14:20:20'},1.0,1.0,1.0,0.0,0.0,0.0,0.0,0.0,0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',1,'structured product',0.0,0.0,0.0,0.0,44);
INSERT INTO FUND (id,user_id,user_manager_id,title,description,keywords,image_url,overview,manager_description,manager_fee,solfini_fee,manager_stake,created,last_updated,volatility,target_return,target_interest,nav,outstanding,inception_return,d1_return,d30_return,holding1,holding1_percent,holding1_name,holding2,holding2_percent,holding2_name,holding3,holding3_percent,holding3_name,holding4,holding4_percent,holding4_name,holding5,holding5_percent,holding5_name,status,strategy,yr1Return,valuation,divYield,benchmark,account_id) VALUES (18,18,18,'test fund abc16','test desc','test 123','','','genius trader',0.0,0.0,25.0,{ts '2018-11-05 05:09:14'},{ts '2018-11-14 14:20:40'},0.0,0.0,0.0,0.0,200.0,0.0,0.0,0.0,0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',0,0.0,'',0,'diversified',0.0,0.0,0.0,0.0,44);

ALTER SEQUENCE FUND_id_seq RESTART WITH 19;



create table EXECUTION_REPORT (
  	id BIGSERIAL PRIMARY KEY NOT NULL,
  	created TIMESTAMP default now(),
  	securityId INT,
  	userId INT,
  	clOrdId VARCHAR(32) DEFAULT NULL,
  	symbol VARCHAR(32),
	side CHAR(8),
	ordType CHAR(8),
	execType CHAR(8),
	ordStatus CHAR(8),
  	orderId BIGINT,
  	secondaryOrderId BIGINT,
  	origOrderId BIGINT,
  	execId BIGINT,
  	secondaryExecId BIGINT,
  	counterpartyId INT,
  	isPositionSideCrossed BOOLEAN,
  	targetStrategy INT,
  	orderQty BIGINT,
  	orderQtyScale INT,
  	leavesQty BIGINT,
  	leavesQtyScale INT,
    cumQty BIGINT,
  	cumQtyScale INT,
    cumQuoteQty BIGINT,
  	price BIGINT,
  	priceScale INT,
  	avgPx BIGINT,
  	avgPxScale INT,
  	lastPx BIGINT,
  	lastPxScale INT,
  	lastQty BIGINT,
  	lastQtyScale INT,
  	stopPx BIGINT,
  	stopPxScale INT,
  	timeInForce CHAR(1),
  	expireTime BIGINT,
  	timestampMillis BIGINT,
  	expireTimeMillis BIGINT,
  	aggressorSide CHAR(8),
  	price2 BIGINT,
  	price2Scale INT,
  	execRestatementReason CHAR(8),
  	sourceSeqNum BIGINT,
  	sourceSendTime BIGINT,
  	snapId BIGINT,
  	kafkaRecordOffset BIGINT,
  	transactionId BIGINT,
  	isLastMessageInTransaction BOOLEAN,
  	decodedTime BIGINT,
  	matchTime BIGINT,
  	publishTime BIGINT,
  	notional double precision,
  	feePositionId INT,
  	feePositionQuantityChange BIGINT,
  	feePositionQuantity BIGINT,
  	settlePositionId INT,
  	settlePositionQuantityChange BIGINT, 
  	settlePositionQuantity BIGINT,
  	isPaidToInsurance BOOLEAN,
  	isHidden BOOLEAN,
  	isLiquidation BOOLEAN
);

   CREATE INDEX EXECUTION_REPORT1 ON EXECUTION_REPORT (userId); 
   CREATE INDEX EXECUTION_REPORT3 ON EXECUTION_REPORT (securityId); 


create table POSITION_REPORT (
   	id BIGSERIAL PRIMARY KEY NOT NULL,
   	reportId BIGINT,
  	created TIMESTAMP default now(),
  	userId INT,
    usdValue double precision,
    usdNotionalPositionValue double precision,
  	usdMaxExposurePositionAndOpenOrdersValue double precision,
	usdOpenOrdersRequiredValue double precision,
	usdMarginValue double precision,
	usdMarginRequiredValue double precision,
	usdMarginMaintValue double precision,
	leverageRatio double precision,
	usdUnrealized double precision,
  	sourceSeqNum BIGINT,
  	sourceSendTime BIGINT,
  	snapId BIGINT,
  	kafkaRecordOffset BIGINT,
  	transactionId BIGINT,
  	isLastMessageInTransaction BOOLEAN,
  	decodedTime BIGINT,
  	matchTime BIGINT,
  	publishTime BIGINT
);
   CREATE INDEX POSITION_REPORT1 ON POSITION_REPORT (userId); 
   



create table POSITION_REPORT_BALANCE (
   	id BIGSERIAL PRIMARY KEY NOT NULL,
   	reportId BIGINT,
  	instrumentId INT,
  	assetType CHAR(8),
  	quantity BIGINT,
  	quantityScale INT,
    availableQuantity BIGINT,
  	availableQuantityScale INT,
    usdCostBasis BIGINT,
  	usdCostBasisScale INT,
    usdAvgCostBasis BIGINT,
  	usdAvgCostBasisScale INT,
  	usdValue double precision,
  	usdUnrealized double precision,
   	usdRealized double precision,
  	quotedUsdMark double precision,
  	settleCoinUsdMark double precision,  	 	 
  	settleCoinUnrealized double precision,
   	settleCoinRealized double precision,
    bankruptPriceInt BIGINT
);


create table USER_WHITELIST_IP (
  	id BIGSERIAL PRIMARY KEY NOT NULL,
  	userId BIGINT,
  	ip VARCHAR(32) DEFAULT NULL,
  	insert_time VARCHAR(32) DEFAULT NULL,
  	created TIMESTAMP default now(),
  	status INT
);
  	
create table USER_WHITELIST_ADDRESS (
  	id BIGSERIAL PRIMARY KEY NOT NULL,
  	userId BIGINT,
  	instrumentId INT,
  	address VARCHAR(64) DEFAULT NULL,
  	insert_time VARCHAR(32) DEFAULT NULL,
  	created TIMESTAMP default now(),
  	status INT
);

drop table if exists user_stat_log;

create table user_stat_log (
    timestamp timestamp without time zone,
    userid integer,
    feetier integer,
    makervolume double precision,
    takervolume double precision,
    makerfillcount bigint,
    takerfillcount bigint,
    ordercount bigint,
    cancelcount bigint,
    openordercount bigint,
    bestordercount0bps bigint,
    bestordercount20bps bigint,
    bestordercount50bps bigint,
    realizedpnl double precision,
    unrealizedpnl double precision
);

drop table if exists user_role;

create table user_role (
  	id BIGSERIAL PRIMARY KEY NOT NULL,
    userId INT,
    account INT,
    role INT,
    status INT,
    created_by INT,
    created TIMESTAMP default now()
);
CREATE INDEX user_role1 ON user_role (userId); 
CREATE INDEX user_role2 ON user_role (account); 
CREATE INDEX user_role3 ON user_role (role); 
CREATE INDEX user_role4 ON user_role (created); 

drop table if exists user_stat_fee_log;

create table user_stat_fee_log (
    id bigint default nextval('user_stat_fee_log_seq'::regclass),
    timestamp timestamp without time zone,
    userid integer,
    feeinstrumentid integer,
    feescale integer,
    feeamount bigint,
    grantuserid integer,
    granttimestamp timestamp without time zone,
    grantamount bigint,
    grantkafkaoffset bigint,
    discountuserid integer,
    discounttimestamp timestamp without time zone,
    discountamount bigint,
    discountkafkaoffset bigint,
    processed boolean default false
);

alter table user_state add minimumfeetier integer default 0;
alter table user_state add upgradefeetier integer default 0;


alter table ADDRESS_STATE add managerUserId INT;
CREATE INDEX ADDRESS_STATE4 ON ADDRESS_STATE (managerUserId); 

alter table ADDRESS_STATE_LOG add managerUserId INT;
CREATE INDEX ADDRESS_STATE_LOG4 ON ADDRESS_STATE_LOG (managerUserId); 

alter table user_state add sso_key VARCHAR(64) DEFAULT NULL;

alter table user_state add frozen INT DEFAULT 0;

alter table user_state add alias_username VARCHAR(128);

alter table user_stat_log add column effectivevolume double precision;

create index execution_report4 on execution_report(orderid);

alter table USER_STATE add column updated TIMESTAMP default now();
CREATE INDEX USER_STATE3 ON USER_STATE (updated);

  CREATE INDEX POSITION_REPORT2 ON POSITION_REPORT (reportid); 
  CREATE INDEX POSITION_REPORT_BALANCE2 ON POSITION_REPORT_BALANCE (reportid); 
  
  alter table POSITION_REPORT add txnType INT DEFAULT 0;
  alter table POSITION_REPORT add execId bigint DEFAULT 0;
  alter table POSITION_REPORT add orderId bigint DEFAULT 0;
  
create table deribit_pair (
    id BIGSERIAL PRIMARY KEY NOT NULL,
    instrumentId INT,
    type INT,
    timestamp bigint,
    symbol VARCHAR(32) DEFAULT NULL,
    kind VARCHAR(32) DEFAULT NULL,
    quote_currency VARCHAR(32) DEFAULT NULL,
    base_currency VARCHAR(32) DEFAULT NULL,
    option_type VARCHAR(32) DEFAULT NULL,
    settlement_period VARCHAR(32) DEFAULT NULL,
    expiration_timestamp bigint,
    creation_timestamp bigint,
    is_active boolean,
    contract_size INT,  
    taker_commission double precision,
    maker_commission double precision,  
    tick_size double precision,
    created TIMESTAMP default now()
);
CREATE INDEX deribit_pair1 ON deribit_pair (instrumentId); 
CREATE INDEX deribit_pair2 ON deribit_pair (symbol); 


create table deribit_last_trade (
    id BIGSERIAL PRIMARY KEY NOT NULL,
    pairId INT,
    instrumentId INT,
    timestamp bigint,
    symbol VARCHAR(32) DEFAULT NULL,
    direction VARCHAR(32) DEFAULT NULL,
    trade_id VARCHAR(32) DEFAULT NULL,
    trade_seq bigint,
    tick_direction INT,
    price double precision,  
    index_price double precision, 
    amount double precision, 
    iv double precision,
    created TIMESTAMP default now()
);
CREATE INDEX deribit_last_trade1 ON deribit_last_trade (instrumentId); 
CREATE INDEX deribit_last_trade2 ON deribit_last_trade (symbol); 
CREATE INDEX deribit_last_trade3 ON deribit_last_trade (pairId); 
CREATE INDEX deribit_last_trade4 ON deribit_last_trade (trade_seq); 

alter table position_report add instrumentId1 INT DEFAULT 0;
alter table position_report add qty1 bigint DEFAULT 0;
alter table position_report add change1 bigint DEFAULT 0;
alter table position_report add instrumentId2 INT DEFAULT 0;
alter table position_report add qty2 bigint DEFAULT 0;
alter table position_report add change2 bigint DEFAULT 0;
alter table position_report add instrumentId3 INT DEFAULT 0;
alter table position_report add qty3 bigint DEFAULT 0;
alter table position_report add change3 bigint DEFAULT 0;
alter table position_report add instrumentId4 INT DEFAULT 0;
alter table position_report add qty4 bigint DEFAULT 0;
alter table position_report add change4 bigint DEFAULT 0;


alter table user_state add holdUSDEInstrumentId INT DEFAULT 0;

ALTER TABLE user_state RENAME COLUMN holdusdeintsrumentid TO holdusdeinstrumentid;

CREATE INDEX POSITION_REPORT3 ON POSITION_REPORT (created); 

alter table user_state add isFirm SMALLINT DEFAULT 0;

alter table user_state add isExchangeStaff SMALLINT DEFAULT 0;
alter table user_state add uiThemeId SMALLINT DEFAULT 0;

alter table SECURITY_DEFINITION_LOG add symbolRollCount INT DEFAULT 0;
alter table SECURITY_DEFINITION_LOG add expireTimeMillis INT DEFAULT 0;
alter table SECURITY_DEFINITION_LOG add expireRollTimeMillis INT DEFAULT 0;

alter table USER_STATE add column updated_2fa TIMESTAMP default now();
alter table USER_STATE add column updated_password TIMESTAMP default now();
alter table USER_STATE add column last_login_time TIMESTAMP default now();
alter table user_state add isIndividual SMALLINT DEFAULT 0;

alter table user_state add isFuturesEnabled SMALLINT DEFAULT 1;
alter table user_state add isOptionsEnabled SMALLINT DEFAULT 1;
alter table user_state add kycTier SMALLINT DEFAULT 1;


alter table USER_STATE add column twitter VARCHAR(128);

alter table user_state add isCommoditiesEnabled SMALLINT DEFAULT 1;
alter table user_state add isEquitiesEnabled SMALLINT DEFAULT 1;
alter table user_state add isLeaderboardExcluded SMALLINT DEFAULT 0;

ALTER TABLE security_definition_log alter column expiretimemillis type bigint;
ALTER TABLE security_definition_log alter column expirerolltimemillis type bigint;

alter table user_state add isToasterEnabled SMALLINT DEFAULT 1;

alter table user_state add depositKycRestriction double precision DEFAULT 0;
alter table user_state add withdrawKycRestriction double precision DEFAULT 0;
alter table user_state add displayCurrency VARCHAR(8) DEFAULT 'USD';

alter table execution_report add submitterId INT DEFAULT 0;


alter table USER_WHITELIST_ADDRESS add name VARCHAR(128);
alter table USER_WHITELIST_ADDRESS add memo VARCHAR(256);

alter table USER_STATE add column notionalPositionCap double precision;
alter table USER_STATE add column leverageCap double precision;



create table FUNDING_RATE_HISTORY (
   	id BIGSERIAL PRIMARY KEY NOT NULL,
  	created TIMESTAMP default now(),
  	pairId INT,
  	symbol VARCHAR(32),
  	fundingRate double precision,
  	markInSettleCoin double precision
);
CREATE INDEX FUNDING_RATE_HISTORY1 ON FUNDING_RATE_HISTORY (pairId); 
   
   alter table user_state add isAuctionsEnabled SMALLINT DEFAULT 1;
   alter table user_state add isDatedFuturesEnabled SMALLINT DEFAULT 1;
   
alter table position_report add txnId BIGINT;
   
alter table FUNDING_RATE_HISTORY add txId BIGINT DEFAULT 0;
alter table FUNDING_RATE_HISTORY add timestamp BIGINT DEFAULT 0;


 alter table position_report add usdMarginableValue double precision;
 
 alter table FUNDING_RATE_HISTORY add vwap double precision;
 alter table FUNDING_RATE_HISTORY add last double precision;
 alter table FUNDING_RATE_HISTORY add usdMark double precision;
 alter table FUNDING_RATE_HISTORY add timePeriodInterest double precision;
 
 alter table USER_STATE add column marginCurveIdOverride INT DEFAULT 0;
 
 CREATE INDEX FUNDING_RATE_HISTORY2 ON FUNDING_RATE_HISTORY (timestamp); 
CREATE INDEX POSITION_REPORT4 ON POSITION_REPORT (txnType); 
CREATE INDEX POSITION_REPORT5 ON POSITION_REPORT (publishtime); 

CREATE TABLE AML_LOG(
	id BIGSERIAL PRIMARY KEY NOT NULL,
	userId INT,
	direction VARCHAR(16),
	chainType VARCHAR(16),
	score double precision,
	publicAddress VARCHAR(256),
	txnId VARCHAR(256),
	response VARCHAR(2048),
	created TIMESTAMP default now()
);


 alter table security_definition_log add column marketType INT DEFAULT 2;
 alter table asset_details_state add  assetLogoUrl varchar(256) null;
 
 
  alter table asset_details_state add      aboutOrg varchar(1024) null;
  alter table asset_details_state add     sdgId varchar(32) null;
  alter table asset_details_state add     standardsVersion varchar(64) null;
  alter table asset_details_state add     methodology varchar(256) null;
  alter table asset_details_state add     projectScale varchar(32) null;
   alter table asset_details_state add    annualEstCredits BIGINT;
   alter table asset_details_state add    marketType varchar(32) null;
   alter table asset_details_state add    sdgImpactList varchar(256) null;
   alter table asset_details_state add    projectFromTime BIGINT;
   alter table asset_details_state add    projectToTime BIGINT;
   alter table asset_details_state add    floorPrice double precision;
   alter table asset_details_state add    ceilingPrice double precision;

 