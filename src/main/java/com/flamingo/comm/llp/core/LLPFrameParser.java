package com.flamingo.comm.llp.core;

public interface LLPFrameParser {

    /**
     * Parses a validated transport frame into a structured LLPFrame.
     *
     * @param rawFrame validated raw frame from transport layer
     * @return parse result containing parsed structure or error
     */
    LLPFrame parse(LLPRawFrame rawFrame);
}