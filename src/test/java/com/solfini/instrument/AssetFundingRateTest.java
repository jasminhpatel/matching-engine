package com.solfini.instrument;

import org.junit.Assert;
import org.junit.Test;

import com.solfini.instrument.AssetFundingRate;

import uk.co.real_logic.artio.fields.DecimalFloat;

public class AssetFundingRateTest {

  @Test
  public void assetId() {
    AssetFundingRate assetFundingRate = new AssetFundingRate();
    Assert.assertEquals(0, assetFundingRate.getAssetId());

    assetFundingRate.setAssetId(100);
    Assert.assertEquals(100, assetFundingRate.getAssetId());
  }

  @Test
  public void getMarkInSettleCoin() {
    AssetFundingRate assetFundingRate = new AssetFundingRate();
    Assert.assertNull(assetFundingRate.getMarkInSettleCoin());

    assetFundingRate.setMarkInSettleCoin(new DecimalFloat(105, 2));
    Assert.assertNotNull(assetFundingRate.getMarkInSettleCoin());
    Assert.assertEquals(105, assetFundingRate.getMarkInSettleCoin().value());
    Assert.assertEquals(2, assetFundingRate.getMarkInSettleCoin().scale());
  }

  @Test
  public void convertToString() {
    AssetFundingRate assetFundingRate = new AssetFundingRate();
    assetFundingRate.setAssetId(100);
    Assert.assertEquals("AssetFundingRate [assetId=100, rate=0, rate_scale=0, markInSettleCoin=0, markInSettleCoin_scale=0]", assetFundingRate.toString());

    assetFundingRate.setRate(new DecimalFloat(200, 2));
    Assert.assertEquals("AssetFundingRate [assetId=100, rate=200, rate_scale=2, markInSettleCoin=0, markInSettleCoin_scale=0]", assetFundingRate.toString());

    assetFundingRate.setMarkInSettleCoin(new DecimalFloat(3000, 3));
    Assert.assertEquals("AssetFundingRate [assetId=100, rate=200, rate_scale=2, markInSettleCoin=3000, markInSettleCoin_scale=3]", assetFundingRate.toString());
  }

  @Test
  public void convertToJSON() {
    AssetFundingRate assetFundingRate = new AssetFundingRate();
    assetFundingRate.setAssetId(100);
    Assert.assertEquals("{\"class\":\"AssetFundingRate\",\"assetId\":100}", assetFundingRate.toJSON());

    assetFundingRate.setRate(new DecimalFloat(200, 2));
    Assert.assertEquals("{\"class\":\"AssetFundingRate\",\"assetId\":100,\"rate\":[200,2]}", assetFundingRate.toJSON());

    assetFundingRate.setMarkInSettleCoin(new DecimalFloat(3000, 3));
    Assert.assertEquals("{\"class\":\"AssetFundingRate\",\"assetId\":100,\"rate\":[200,2],\"markInSettleCoin\":[3000,3]}", assetFundingRate.toJSON());
  }
}
