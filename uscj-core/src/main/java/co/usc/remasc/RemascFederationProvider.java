package co.usc.remasc;

import co.usc.ulordj.store.BlockStoreException;
import co.usc.core.UscAddress;
import co.usc.peg.BridgeSupport;
import org.ethereum.crypto.ECKey;

import java.io.IOException;

/**
 * Created by ajlopez on 14/11/2017.
 */
public class RemascFederationProvider {
    private BridgeSupport bridgeSupport;

    public RemascFederationProvider(BridgeSupport bridgeSupport) throws IOException, BlockStoreException {
        this.bridgeSupport = bridgeSupport;
    }

    public int getFederationSize() throws IOException {
        return this.bridgeSupport.getFederationSize().intValue();
    }

    public UscAddress getFederatorAddress(int n) {
        byte[] publicKey = this.bridgeSupport.getFederatorPublicKey(n);
        return new UscAddress(ECKey.fromPublicOnly(publicKey).getAddress());
    }
}
