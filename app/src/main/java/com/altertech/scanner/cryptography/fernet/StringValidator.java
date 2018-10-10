package com.altertech.scanner.cryptography.fernet;

/*
  Created by oshevchuk on 10.10.2018
 */

import java.nio.charset.Charset;
import java.util.function.Function;

import static com.altertech.scanner.cryptography.fernet.Constants.charset;


public interface StringValidator extends Validator<String> {

    default Charset getCharset() {
        return charset;
    }

    default Function<byte[], String> getTransformer() {
        return bytes -> new String(bytes, getCharset());
    }

}