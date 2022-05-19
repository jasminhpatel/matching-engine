#!/usr/bin/env bash

# This script launches the AffiliateProgram to process user statistics and initiate fee tier transfers, referral discounts and new user rebates.
java -Xms1024m -Xmx88192m -cp lib/match-engine-v2-0.0.1-SNAPSHOT.jar:lib/* com.solfini.util.affiliate.AffiliateProgram $@
