/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.usc.net.eth;

import co.usc.config.UscSystemProperties;
import co.usc.net.messages.Message;
import org.ethereum.net.eth.message.EthMessage;
import org.ethereum.net.eth.message.EthMessageCodes;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

/**
 * Created by ajlopez on 5/14/2016.
 */
public class UscMessage extends EthMessage {
    private final UscSystemProperties config;
    private Message message;

    public UscMessage(UscSystemProperties config, byte[] encoded) {
        super(encoded);
        this.config = config;
    }

    public UscMessage(UscSystemProperties config, Message message) {
        this.config = config;
        this.message = message;
        this.parsed = true;
    }

    @Override
    public EthMessageCodes getCommand() {
        return EthMessageCodes.USC_MESSAGE;
    }

    public Message getMessage() {
        if (!this.parsed) {
            parse();
        }

        return this.message;
    }

    protected void parse() {
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);

        this.message = Message.create((RLPList) paramsList.get(0));

        this.parsed = true;
    }

    @Override
    public byte[] getEncoded() {
        if (encoded == null) {
            encode();
        }

        return encoded;
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    protected void encode() {
        byte[] msg = this.message.getEncoded();

        this.encoded = RLP.encodeList(msg);
    }


    @Override
    public String toString() {
        if (!parsed) {
            parse();
        }
        
        return "[" + this.getCommand().name() +
                " message=" + this.message +
                "]";
    }
}
