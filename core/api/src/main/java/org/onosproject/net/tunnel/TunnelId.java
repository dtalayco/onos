/*
 * Copyright 2015 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.net.tunnel;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Representation of a Tunnel Id.
 */
public final class TunnelId {
    private final long value;

    /**
     * Creates an tunnel identifier from the specified tunnel.
     *
     * @param value long value
     * @return tunnel identifier
     */
    public static TunnelId valueOf(long value) {
        return new TunnelId(value);
    }

    public static TunnelId valueOf(String value) {
        checkArgument(value.startsWith("0x"));
         return new TunnelId(Long.parseLong(value.substring("0x".length()), 16));
    }

    /**
     * Constructor for serializer.
     */
    TunnelId() {
        this.value = 0;
    }

    /**
     * Constructs the ID corresponding to a given long value.
     *
     * @param value the underlying value of this ID
     */
    TunnelId(long value) {
        this.value = value;
    }

    /**
     * Returns the backing value.
     *
     * @return the value
     */
    public long id() {
        return value;
    }

    @Override
    public int hashCode() {
        return (int) (value ^ (value >>> 32));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof TunnelId)) {
            return false;
        }
        TunnelId that = (TunnelId) obj;
        return this.value == that.value;
    }

    @Override
    public String toString() {
        return "0x" + Long.toHexString(value);
    }

}
