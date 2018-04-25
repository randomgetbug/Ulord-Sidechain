/*
 * This file is part of RskJ
 * Copyright (C) 2017 USC Labs Ltd.
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

package co.usc.net.handler.txvalidator;

import co.usc.config.UscSystemProperties;
import co.usc.core.Coin;
import co.usc.core.UscAddress;
import org.ethereum.core.*;

import java.math.BigInteger;

/**
 * Checks if a minimum gas limit estimated based on the data is lower than
 * the gas limit of the transaction
 */
public class TxValidatorIntrinsicGasLimitValidator implements TxValidatorStep {

    private final UscSystemProperties config;

    public TxValidatorIntrinsicGasLimitValidator(UscSystemProperties config) {
        this.config = config;
    }

    @Override
    public boolean validate(Transaction tx, AccountState state, BigInteger gasLimit, Coin minimumGasPrice, long bestBlockNumber, boolean isFreeTx) {
        BlockHeader blockHeader = new BlockHeader(new byte[]{},
                new byte[]{},
                UscAddress.nullAddress().getBytes(),
                new Bloom().getData(),
                new byte[]{},
                bestBlockNumber,
                new byte[]{},
                0,
                0,
                new byte[]{},
                new byte[]{},
                new byte[]{},
                new byte[]{},
                new byte[]{},
                0
        );
        Block block = new Block(blockHeader);
        return BigInteger.valueOf(tx.transactionCost(config, block)).compareTo(tx.getGasLimitAsInteger()) <= 0;
    }
}
