/*
 * This file is part of USC
 * Copyright (C) 2018 Ulord core team.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.usc.peg;

import co.usc.ulordj.core.*;
import co.usc.ulordj.script.Script;
import co.usc.ulordj.store.BlockStoreException;
import co.usc.ulordj.store.UldBlockStore;
import co.usc.ulordj.wallet.Wallet;
import co.usc.config.BridgeConstants;
import co.usc.core.UscAddress;
import co.usc.peg.ulord.UscAllowUnconfirmedCoinSelector;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Transaction;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Oscar Guindzberg
 */
public class BridgeUtils {

    private static final Logger logger = LoggerFactory.getLogger("BridgeUtils");
    private static Map<Sha256Hash, Sha256Hash> parentMap = new HashMap<>();

    public static StoredBlock getStoredBlockAtHeight(UldBlockStore blockStore, int height) throws BlockStoreException {
        StoredBlock storedBlock = blockStore.getChainHead();
        Sha256Hash blockHash = storedBlock.getHeader().getHash();
        int headHeight = storedBlock.getHeight();

        if (height > headHeight) {
            return null;
        }
        for (int i = 0; i < (headHeight - height); i++) {
            if (blockHash == null) {
                return null;
            }

            Sha256Hash prevBlockHash = parentMap.get(blockHash);

            if (prevBlockHash == null) {
                StoredBlock currentBlock = blockStore.get(blockHash);

                if (currentBlock == null) {
                    return null;
                }

                prevBlockHash = currentBlock.getHeader().getPrevBlockHash();
                parentMap.put(blockHash, prevBlockHash);
            }

            blockHash = prevBlockHash;
        }

        if (blockHash == null) {
            return null;
        }

        storedBlock = blockStore.get(blockHash);

        if (storedBlock != null) {
            if (storedBlock.getHeight() != height) {
                throw new IllegalStateException("Block height is " + storedBlock.getHeight() + " but should be " + headHeight);
            }
            return storedBlock;
        } else {
            return null;
        }
    }

    public static Wallet getFederationNoSpendWallet(Context uldContext, Federation federation) {
        return getFederationsNoSpendWallet(uldContext, Arrays.asList(federation));
    }

    public static Wallet getFederationsNoSpendWallet(Context uldContext, List<Federation> federations) {
        Wallet wallet = new BridgeUldWallet(uldContext, federations);
        federations.forEach(federation -> wallet.addWatchedAddress(federation.getAddress(), federation.getCreationTime().toEpochMilli()));
        return wallet;
    }

    public static Wallet getFederationSpendWallet(Context uldContext, Federation federation, List<UTXO> utxos) {
        return getFederationsSpendWallet(uldContext, Arrays.asList(federation), utxos);
    }

    public static Wallet getFederationsSpendWallet(Context uldContext, List<Federation> federations, List<UTXO> utxos) {
        Wallet wallet = new BridgeUldWallet(uldContext, federations);

        UscUTXOProvider utxoProvider = new UscUTXOProvider(uldContext.getParams(), utxos);
        wallet.setUTXOProvider(utxoProvider);
        federations.stream().forEach(federation -> {
            wallet.addWatchedAddress(federation.getAddress(), federation.getCreationTime().toEpochMilli());
        });
        wallet.setCoinSelector(new UscAllowUnconfirmedCoinSelector());
        return wallet;
    }

    private static boolean scriptCorrectlySpendsTx(UldTransaction tx, int index, Script script) {
        try {
            TransactionInput txInput = tx.getInput(index);
            txInput.getScriptSig().correctlySpends(tx, index, script, Script.ALL_VERIFY_FLAGS);
            return true;
        } catch (ScriptException se) {
            return false;
        }
    }

    public static boolean isLockTx(UldTransaction tx, List<Federation> federations, Context uldContext, BridgeConstants bridgeConstants) {
        // First, check tx is not a typical release tx (tx spending from the any of the federation addresses and
        // optionally sending some change to any of the federation addresses)
        for (int i = 0; i < tx.getInputs().size(); i++) {
            final int index = i;
            if (federations.stream().anyMatch(federation -> scriptCorrectlySpendsTx(tx, index, federation.getP2SHScript()))) {
                return false;
            }
        }

        Wallet federationsWallet = BridgeUtils.getFederationsNoSpendWallet(uldContext, federations);
        Coin valueSentToMe = tx.getValueSentToMe(federationsWallet);

        int valueSentToMeSignum = valueSentToMe.signum();
        if (valueSentToMe.isLessThan(bridgeConstants.getMinimumLockTxValue())) {
            logger.warn("Someone sent to the federation less than {} satoshis", bridgeConstants.getMinimumLockTxValue());
        }
        return (valueSentToMeSignum > 0 && !valueSentToMe.isLessThan(bridgeConstants.getMinimumLockTxValue()));
    }

    public static boolean isLockTx(UldTransaction tx, Federation federation, Context uldContext, BridgeConstants bridgeConstants) {
        return isLockTx(tx, Arrays.asList(federation), uldContext, bridgeConstants);
    }

    public static boolean isReleaseTx(UldTransaction tx, Federation federation, BridgeConstants bridgeConstants) {
        int i = 0;
        for (TransactionInput transactionInput : tx.getInputs()) {
            try {
                transactionInput.getScriptSig().correctlySpends(tx, i, federation.getP2SHScript(), Script.ALL_VERIFY_FLAGS);
                // There is an input spending from the federation address, this is a release tx
                return true;
            } catch (ScriptException se) {
                // do-nothing, input does not spends from the federation address
            }
            i++;
        }
        return false;
    }

    public static boolean isMigrationTx(UldTransaction uldTx, Federation activeFederation, Federation retiringFederation, Context uldContext, BridgeConstants bridgeConstants) {
        if (retiringFederation == null) {
            return false;
        }
        boolean moveFromRetiring = isReleaseTx(uldTx, retiringFederation, bridgeConstants);
        boolean moveToActive = isLockTx(uldTx, activeFederation, uldContext, bridgeConstants);

        return moveFromRetiring && moveToActive;
    }

    public static Address recoverUldAddressFromEthTransaction(org.ethereum.core.Transaction tx, NetworkParameters networkParameters) {
        org.ethereum.crypto.ECKey key = tx.getKey();
        byte[] pubKey = key.getPubKey(true);
        return UldECKey.fromPublicOnly(pubKey).toAddress(networkParameters);
    }

    public static boolean isFreeBridgeTx(SystemProperties config, Transaction uscTx, long blockNumber) {
        BlockchainNetConfig blockchainConfig = config.getBlockchainConfig();
        UscAddress receiveAddress = uscTx.getReceiveAddress();
        if (receiveAddress.equals(UscAddress.nullAddress())) {
            return false;
        }

        BridgeConstants bridgeConstants = blockchainConfig.getCommonConstants().getBridgeConstants();

        // Temporary assumption: if areBridgeTxsFree() is true then the current federation
        // must be the genesis federation.
        // Once the original federation changes, txs are always paid.
        return PrecompiledContracts.BRIDGE_ADDR.equals(receiveAddress) &&
               blockchainConfig.getConfigForBlock(blockNumber).areBridgeTxsFree() &&
               uscTx.acceptTransactionSignature(config.getBlockchainConfig().getCommonConstants().getChainId()) &&
               (
                       isFromFederateMember(uscTx, bridgeConstants.getGenesisFederation()) ||
                       isFromFederationChangeAuthorizedSender(uscTx, bridgeConstants) ||
                       isFromLockWhitelistChangeAuthorizedSender(uscTx, bridgeConstants) ||
                       isFromFeePerKbChangeAuthorizedSender(uscTx, bridgeConstants)
               );
    }

    private static boolean isFromFederateMember(org.ethereum.core.Transaction uscTx, Federation federation) {
        return federation.hasMemberWithUscAddress(uscTx.getSender().getBytes());
    }

    private static boolean isFromFederationChangeAuthorizedSender(org.ethereum.core.Transaction uscTx, BridgeConstants bridgeConfiguration) {
        AddressBasedAuthorizer authorizer = bridgeConfiguration.getFederationChangeAuthorizer();
        return authorizer.isAuthorized(uscTx);
    }

    private static boolean isFromLockWhitelistChangeAuthorizedSender(org.ethereum.core.Transaction uscTx, BridgeConstants bridgeConfiguration) {
        AddressBasedAuthorizer authorizer = bridgeConfiguration.getLockWhitelistChangeAuthorizer();
        return authorizer.isAuthorized(uscTx);
    }

    private static boolean isFromFeePerKbChangeAuthorizedSender(org.ethereum.core.Transaction uscTx, BridgeConstants bridgeConfiguration) {
        AddressBasedAuthorizer authorizer = bridgeConfiguration.getFeePerKbChangeAuthorizer();
        return authorizer.isAuthorized(uscTx);
    }
}
