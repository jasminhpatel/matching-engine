#!/bin/bash

endpoint="https://trading-api.solfini-qa.com"
admin=10
token="CzMt4Ap6I86BMzXP"
secret="Ywww9GBF2Mtiu4iL"

# endpoint="https://solfini.io"
# admin=10
# token="MNe6FSeO0uwuT4Pi"
# secret="qmZdArRLGWEukweN"

echo "endpoint: $endpoint"

ETH=2
BTC=3
USD=31

function show() {
    account=$1
    instrument=$2

    java -cp solfini-api-0.0.1-SNAPSHOT.jar:admin-messaging-0.0.1-SNAPSHOT.jar:commons-cli-1.4.jar:gson-2.4.jar com.solfini.springmvc.BalanceUpdateTool --endpoint $endpoint --admin $admin --token $token --secret $secret --account $account --instrument $instrument
}

function deposit() {
    account=$1
    instrument=$2
    amount=$3
    scale=$4

    java -cp solfini-api-0.0.1-SNAPSHOT.jar:admin-messaging-0.0.1-SNAPSHOT.jar:commons-cli-1.4.jar:gson-2.4.jar com.solfini.springmvc.BalanceUpdateTool --endpoint $endpoint --admin $admin --token $token --secret $secret --account $account --instrument $instrument --deposit $amount --scale $scale
}

function withdraw() {
    account=$1
    instrument=$2
    amount=$3
    scale=$4

    java -cp solfini-api-0.0.1-SNAPSHOT.jar:admin-messaging-0.0.1-SNAPSHOT.jar:commons-cli-1.4.jar:gson-2.4.jar com.solfini.springmvc.BalanceUpdateTool --endpoint $endpoint --admin $admin --token $token --secret $secret --account $account --instrument $instrument --withdraw $amount --scale $scale
}

# echo
# echo "Current balances"
# for account in 31 29 32 23 33 14
# do
#     show $account $BTC
#     show $account $USD
# done

# echo
# echo "Deposits"

# show 31 USD
# 1. Thomas Chladek
# User Email: thomas.chladek@gmail.com
# Account ID: a7e59e3d-30fd-46fe-aea2-79dd41c122f3
# Currency: BTC
# Amount: 0.00000592
# Trading User ID: 31
#deposit 31 $BTC 592 8

# 2. Jean El Khoury
# User Email: jelkhoury@mac.com
# Account ID: ee25d003-6c2e-4fa8-80c2-dc9f98eda6ed
# Currency: USD
# Amount: 933.0905
# Trading User ID: 29
#deposit 29 $USD 9330905 4

# 3. Masahide Hoshi
# User Email: masahide.hoshi@yahoo.com
# Account ID: 5ec35c26-7589-4e36-9441-48f3c6a7ff40
# Currency: BTC
# Amount: 0.000004
# Currency: USD
# Amount: 0.0025
# Trading User ID: 32
#--deposit 32 $BTC  4 6
#deposit 32 $USD 25 4

# 4. Zhang Zi Wei
# User Email: a.zhang119@gmail.com
# Account ID: b4406285-dbff-4571-919b-f12a341cd991
# Currency: USD
# Amount: 0.0247
# Currency: BTC
# Amount: 0.0015
# Trading User ID: 23
#--deposit 23 $BTC 15 4
#deposit 23 $USD 247 4

# 5. Wei Zhu
# User Email: zhuwei777@outlook.com
# Account ID: 5c92feea-fed4-428b-90d1-404de8482bf8
# Currency: USD
# Amount: 0.0024
# Currency: BTC
# Amount: 0.000006
# Trading User ID: 33
#deposit 33 $BTC 6 6
#deposit 33 $USD 24 4

# 6. Revenue Account
# Currency: BTC
# Amount: 0.014277470
# Currency: USD
# Amount: 120.00
# Trading User ID: 14
#deposit 14 $BTC 14277470 9
#deposit 14 $USD 12000 2

# dust adjustments
#deposit 31 $BTC 1 6


# sleep 10
# echo
# echo "New Balances"
# for account in 31 29 32 23 33 14
# do
#     show $account $BTC
#     show $account $USD
# done

# deposit for ITSM-398
echo "balance"
show 22 $ETH
echo "withdraw"
#deposit 22 $ETH 1000000 6
withdraw 22 $ETH 1000000 6
sleep 10
echo "balance"
show 22 $ETH
