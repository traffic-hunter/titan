package org.traffichunter.titan.core.util.channel.chain;

import org.jspecify.annotations.Nullable;

public interface LinkedHandlerChainNode<CTX> extends HandlerChain<CTX> {

    @Nullable LinkedHandlerChainNode<CTX> next();

    void next(@Nullable LinkedHandlerChainNode<CTX> next);
}