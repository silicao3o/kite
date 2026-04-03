package com.lite_k8s.node;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

@FunctionalInterface
public interface JSchSessionFactory {
    Session create(String user, String host, int port, String keyPath, String passphrase) throws JSchException;
}
