package com.kanglong.m3u8.http.pool;

import com.kanglong.m3u8.util.Preconditions;

import java.util.Objects;

public class ScopedIdentity {

    private final String identity;

    private final String fullIdentity;

    private final ScopedIdentity parent;

    public ScopedIdentity(String identity) {
        this(identity, null);
    }

    public ScopedIdentity(String identity, ScopedIdentity parent) {
        this.identity = Preconditions.checkNotBlank(identity);

        this.parent = parent;
        this.fullIdentity = toFullIdentity(identity, parent);
    }

    public String getIdentity() {
        return identity;
    }

    public String getFullIdentity() {
        return fullIdentity;
    }

    public ScopedIdentity getParent() {
        return parent;
    }

    private String toFullIdentity(String identity, ScopedIdentity parent) {
        String fullIdentity = "";
        if (null != parent) {
            fullIdentity = parent.getFullIdentity() + "-";
        }
        return fullIdentity + identity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ScopedIdentity that = (ScopedIdentity) o;
        return Objects.equals(identity, that.identity) && Objects.equals(fullIdentity, that.fullIdentity) && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
        if (null != fullIdentity) {
            return Objects.hash(fullIdentity);
        }
        return Objects.hash(identity, parent);
    }
}
