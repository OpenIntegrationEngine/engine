package com.mirth.connect.connectors.file.filesystems;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.mirth.connect.connectors.file.FTPSchemeProperties;
import com.mirth.connect.connectors.file.FileScheme;
import com.mirth.connect.connectors.file.FileSystemConnectionOptions;
import com.mirth.connect.connectors.file.S3SchemeProperties;

public class FileSystemConnectionFactoryTest {

	@Test
	public void testFTPGetPoolKeyNoInitialCommands() {
		List<String> commands = null;
		FTPSchemeProperties ftpSchemeProps = new FTPSchemeProperties();
		ftpSchemeProps.setInitialCommands(commands);
		FileSystemConnectionOptions options = new FileSystemConnectionOptions(true, "username", "password", ftpSchemeProps);
		FileSystemConnectionFactory factory = new FileSystemConnectionFactory(FileScheme.FTP, options, "host", 9000, false, false, 0);
		
		String expected = "ftp://" + "username" + ":" + "password" + "@" + "host" + ":" + 9000;
		assertEquals(expected, factory.getPoolKey());
	}
	
	@Test
	public void testFTPGetPoolKeyNoSchemeProperties() {
		FTPSchemeProperties ftpSchemeProps = null;
		FileSystemConnectionOptions options = new FileSystemConnectionOptions(true, "username", "password", ftpSchemeProps);
		FileSystemConnectionFactory factory = new FileSystemConnectionFactory(FileScheme.FTP, options, "host", 9000, false, false, 0);
		
		String expected = "ftp://" + "username" + ":" + "password" + "@" + "host" + ":" + 9000;
		assertEquals(expected, factory.getPoolKey());
	}
	
	@Test
	public void testFTPGetPoolKeyWithInitialCommands() {
		List<String> commands = new ArrayList<>();
		commands.add("testcommand1");
		commands.add("testcommand2");
		FTPSchemeProperties ftpSchemeProps = new FTPSchemeProperties();
		ftpSchemeProps.setInitialCommands(commands);
		FileSystemConnectionOptions options = new FileSystemConnectionOptions(true, "username", "password", ftpSchemeProps);
		FileSystemConnectionFactory factory = new FileSystemConnectionFactory(FileScheme.FTP, options, "host", 9000, false, false, 0);
		
		String expected = "ftp://" + "username" + ":" + "password" + "@" + "host" + ":" + 9000 + ":" + commands.get(0) + ":" + commands.get(1);
		assertEquals(expected, factory.getPoolKey());
	}
	
	@Test
	public void testS3GetPoolKeyWithoutDefaultChain() {
		FileSystemConnectionFactory factory = s3Factory(false, "username", "password");
		
		assertEquals("s3://username:password@us-west-2", factory.getPoolKey());
	}
	
	@Test
	public void testS3GetPoolKeyDefaultChainWithCredentials() {
		// The connection signs with credentials that are set even when the default chain is on, so
		// messages with different credentials must not share a pool
		FileSystemConnectionFactory factory = s3Factory(true, "username", "password");
		
		assertEquals("s3://username:password@us-west-2", factory.getPoolKey());
	}
	
	@Test
	public void testS3GetPoolKeyDefaultChainWithoutCredentials() {
		assertEquals("s3://@us-west-2", s3Factory(true, "", "").getPoolKey());
		assertEquals("s3://@us-west-2", s3Factory(true, null, null).getPoolKey());
	}
	
	private static FileSystemConnectionFactory s3Factory(boolean useDefaultCredentialProviderChain, String username, String password) {
		S3SchemeProperties s3SchemeProps = new S3SchemeProperties();
		s3SchemeProps.setUseDefaultCredentialProviderChain(useDefaultCredentialProviderChain);
		s3SchemeProps.setUseTemporaryCredentials(false);
		s3SchemeProps.setRegion("us-west-2");
		FileSystemConnectionOptions options = new FileSystemConnectionOptions(false, username, password, s3SchemeProps);
		return new FileSystemConnectionFactory(FileScheme.S3, options, "bucket", -1, false, false, 0);
	}
	
	
}
