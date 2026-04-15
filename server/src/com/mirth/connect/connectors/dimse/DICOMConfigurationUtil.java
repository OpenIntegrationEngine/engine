/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dimse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.connectors.dimse.dicom.OieDicomReceiver;
import com.mirth.connect.connectors.dimse.dicom.OieDicomSender;
import com.mirth.connect.util.MirthSSLUtil;

public class DICOMConfigurationUtil {

    private static final Logger logger = LogManager.getLogger(DICOMConfigurationUtil.class);

    public static void configureReceiver(OieDicomReceiver receiver, DICOMReceiver connector, DICOMReceiverProperties connectorProperties, String[] protocols) throws Exception {
        if (!StringUtils.equals(connectorProperties.getTls(), "notls")) {
            if (connectorProperties.getTls().equals("without")) {
                logger.warn("DICOM receiver configured with TLS NULL encryption (SSL_RSA_WITH_NULL_SHA). "
                        + "This provides authentication only — data is sent in cleartext. "
                        + "Consider using AES encryption instead.");
                receiver.setTlsWithoutEncryption();
            } else if (connectorProperties.getTls().equals("3des")) {
                logger.warn("DICOM receiver configured with deprecated 3DES cipher suite. "
                        + "Consider using AES encryption instead.");
                receiver.setTls3DES_EDE_CBC();
            } else if (connectorProperties.getTls().equals("aes")) {
                receiver.setTlsAES_128_CBC();
            }

            String trustStore = connector.getReplacer().replaceValues(connectorProperties.getTrustStore(), connector.getChannelId(), connector.getChannel().getName());
            if (StringUtils.isNotBlank(trustStore)) {
                receiver.setTrustStoreURL(trustStore);
            }

            String trustStorePW = connector.getReplacer().replaceValues(connectorProperties.getTrustStorePW(), connector.getChannelId(), connector.getChannel().getName());
            if (StringUtils.isNotBlank(trustStorePW)) {
                receiver.setTrustStorePassword(trustStorePW);
            }

            String keyPW = connector.getReplacer().replaceValues(connectorProperties.getKeyPW(), connector.getChannelId(), connector.getChannel().getName());
            if (StringUtils.isNotBlank(keyPW)) {
                receiver.setKeyPassword(keyPW);
            }

            String keyStore = connector.getReplacer().replaceValues(connectorProperties.getKeyStore(), connector.getChannelId(), connector.getChannel().getName());
            if (StringUtils.isNotBlank(keyStore)) {
                receiver.setKeyStoreURL(keyStore);
            }

            String keyStorePW = connector.getReplacer().replaceValues(connectorProperties.getKeyStorePW(), connector.getChannelId(), connector.getChannel().getName());
            if (StringUtils.isNotBlank(keyStorePW)) {
                receiver.setKeyStorePassword(keyStorePW);
            }

            receiver.setTlsNeedClientAuth(connectorProperties.isNoClientAuth());

            protocols = ArrayUtils.clone(protocols);

            if (connectorProperties.isNossl2()) {
                if (ArrayUtils.contains(protocols, "SSLv2Hello")) {
                    List<String> protocolsList = new ArrayList<String>(Arrays.asList(protocols));
                    protocolsList.remove("SSLv2Hello");
                    protocols = protocolsList.toArray(new String[protocolsList.size()]);
                }
            } else if (!ArrayUtils.contains(protocols, "SSLv2Hello")) {
                List<String> protocolsList = new ArrayList<String>(Arrays.asList(protocols));
                protocolsList.add("SSLv2Hello");
                protocols = protocolsList.toArray(new String[protocolsList.size()]);
            }

            receiver.setTlsProtocol(MirthSSLUtil.getEnabledHttpsProtocols(protocols));

            receiver.initTLS();
        }
    }

    public static void configureSender(OieDicomSender sender, DICOMDispatcher connector, DICOMDispatcherProperties connectorProperties, String[] protocols) throws Exception {
        if (connectorProperties.getTls() != null && !connectorProperties.getTls().equals("notls")) {
            if (connectorProperties.getTls().equals("without")) {
                logger.warn("DICOM sender configured with TLS NULL encryption (SSL_RSA_WITH_NULL_SHA). "
                        + "This provides authentication only — data is sent in cleartext. "
                        + "Consider using AES encryption instead.");
                sender.setTlsWithoutEncryption();
            }
            if (connectorProperties.getTls().equals("3des")) {
                logger.warn("DICOM sender configured with deprecated 3DES cipher suite. "
                        + "Consider using AES encryption instead.");
                sender.setTls3DES_EDE_CBC();
            }
            if (connectorProperties.getTls().equals("aes"))
                sender.setTlsAES_128_CBC();
            if (StringUtils.isNotBlank(connectorProperties.getTrustStore()))
                sender.setTrustStoreURL(connectorProperties.getTrustStore());
            if (StringUtils.isNotBlank(connectorProperties.getTrustStorePW()))
                sender.setTrustStorePassword(connectorProperties.getTrustStorePW());
            if (StringUtils.isNotBlank(connectorProperties.getKeyPW()))
                sender.setKeyPassword(connectorProperties.getKeyPW());
            if (StringUtils.isNotBlank(connectorProperties.getKeyStore()))
                sender.setKeyStoreURL(connectorProperties.getKeyStore());
            if (StringUtils.isNotBlank(connectorProperties.getKeyStorePW()))
                sender.setKeyStorePassword(connectorProperties.getKeyStorePW());
            sender.setTlsNeedClientAuth(connectorProperties.isNoClientAuth());

            protocols = ArrayUtils.clone(protocols);

            if (connectorProperties.isNossl2()) {
                if (ArrayUtils.contains(protocols, "SSLv2Hello")) {
                    List<String> protocolsList = new ArrayList<String>(Arrays.asList(protocols));
                    protocolsList.remove("SSLv2Hello");
                    protocols = protocolsList.toArray(new String[protocolsList.size()]);
                }
            } else if (!ArrayUtils.contains(protocols, "SSLv2Hello")) {
                List<String> protocolsList = new ArrayList<String>(Arrays.asList(protocols));
                protocolsList.add("SSLv2Hello");
                protocols = protocolsList.toArray(new String[protocolsList.size()]);
            }

            protocols = MirthSSLUtil.getEnabledHttpsProtocols(protocols);

            sender.setTlsProtocol(protocols);

            sender.initTLS();
        }
    }
}
