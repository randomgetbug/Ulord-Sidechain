/*
 * This file is part of RskJ
 * Copyright (C) 2017 USC Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.facade;

import org.ethereum.core.Transaction;

import java.util.List;

/**
 * @author Mikhail Kalinin
 * @since 28.09.2015
 */
public interface TransactionPool {

    /**
     * @return pending state repository
     */
    org.ethereum.core.Repository getRepository();

    /**
     * @return list of pending transactions
     */
    List<Transaction> getPendingTransactions();
}
