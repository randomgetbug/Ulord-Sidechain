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

package co.usc.net.discovery;

import io.netty.channel.Channel;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mario on 15/02/17.
 */
public class UDPTestChannel extends UDPChannel{

    private List<DiscoveryEvent> eventsWritten = new ArrayList<>();

    public UDPTestChannel(Channel ch, PeerExplorer peerExplorer) {
        super(ch, peerExplorer);
    }

    @Override
    public void write(DiscoveryEvent discoveryEvent) {
        eventsWritten.add(discoveryEvent);
    }

    public void clearEvents() {
        this.eventsWritten.clear();
    }

    public List<DiscoveryEvent> getEventsWritten() {
        return this.eventsWritten;
    }
}
