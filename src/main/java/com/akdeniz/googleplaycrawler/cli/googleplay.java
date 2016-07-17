package com.akdeniz.googleplaycrawler.cli;

import java.io.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.session.IoSession;

import com.akdeniz.googleplaycrawler.GooglePlayAPI;
import com.akdeniz.googleplaycrawler.GooglePlayAPI.RECOMMENDATION_TYPE;
import com.akdeniz.googleplaycrawler.GooglePlayAPI.REVIEW_SORT;
import com.akdeniz.googleplaycrawler.GooglePlayException;
import com.akdeniz.googleplaycrawler.GooglePlay.AppDetails;
import com.akdeniz.googleplaycrawler.GooglePlay.BrowseLink;
import com.akdeniz.googleplaycrawler.GooglePlay.BrowseResponse;
import com.akdeniz.googleplaycrawler.GooglePlay.BulkDetailsEntry;
import com.akdeniz.googleplaycrawler.GooglePlay.BulkDetailsResponse;
import com.akdeniz.googleplaycrawler.GooglePlay.DetailsResponse;
import com.akdeniz.googleplaycrawler.GooglePlay.DocV2;
import com.akdeniz.googleplaycrawler.GooglePlay.GetReviewsResponse;
import com.akdeniz.googleplaycrawler.GooglePlay.ListResponse;
import com.akdeniz.googleplaycrawler.GooglePlay.Offer;
import com.akdeniz.googleplaycrawler.GooglePlay.ReviewResponse;
import com.akdeniz.googleplaycrawler.GooglePlay.SearchResponse;
import com.akdeniz.googleplaycrawler.gsf.GoogleServicesFramework.BindAccountResponse;
import com.akdeniz.googleplaycrawler.gsf.GoogleServicesFramework.LoginResponse;
import com.akdeniz.googleplaycrawler.gsf.packets.BindAccountRequestPacket;
import com.akdeniz.googleplaycrawler.gsf.packets.HeartBeatPacket;
import com.akdeniz.googleplaycrawler.gsf.packets.LoginRequestPacket;
import com.akdeniz.googleplaycrawler.gsf.MTalkConnector;
import com.akdeniz.googleplaycrawler.gsf.MessageFilter;
import com.akdeniz.googleplaycrawler.gsf.NotificationListener;
import com.akdeniz.googleplaycrawler.Utils;


import net.sourceforge.argparse4j.impl.choice.CollectionArgumentChoice;
import net.sourceforge.argparse4j.inf.Argument;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.ArgumentType;


/**
 *
 * @author akdeniz
 *
 */
public class googleplay {

	private static final String DELIMETER = ";";

	private GooglePlayAPI service;
	String androidid = "";
	String email = "";
	String password = "";
	String securityToken = "";
	String localization ="en-us";
	String category = "";
	String subcategory = "";
	Integer offset = 0;
	Integer number = 50;
	String command = "";
	String host = null;
	Integer port = null;
	String packageName = "";
	RECOMMENDATION_TYPE type;
	REVIEW_SORT sort ;
	String query = "";


	public static final String LIST_HEADER = new StringJoiner(DELIMETER).add("Title").add("Package").add("Creator")
			.add("Price").add("Upload Date").add("Installation Size").add("Number Of Downloads").toString();
	public static final String CATEGORIES_HEADER = new StringJoiner(DELIMETER).add("ID").add("Name").toString();
	public static final String SUBCATEGORIES_HEADER = new StringJoiner(DELIMETER).add("ID").add("Title").toString();

	private static final int TIMEOUT = 10000;

	public googleplay() {
	}

	public googleplay(String androidid) {
		this.androidid = androidid;
	}

	public googleplay(String androidid, String securityToken) {
		this.androidid = androidid;
		this.securityToken = securityToken;
	}


	public googleplay(String androidid, String email, String password) {
		this.androidid = androidid;
		this.email = email;
		this.password =password;
	}

	public void main(String command) throws Exception {
		this.command = command;

		try {
			switch (command) {
				case "checkin":
					checkinCommand();
					break;
				case "download":
					List<String> packages = new ArrayList<>();
					packages.add(packageName);
					downloadCommand(packages);
					break;
				case "list":
					listCommand();
					break;
				case "categories":
					categoriesCommand();
					break;
				case "search":
					searchCommand("");
					break;
				case "permissions":
					permissionsCommand();
					break;
				case "reviews":
					reviewsCommand();
					break;
				case "register":
					registerCommand();
					break;
				case "usegcm":
					useGCMCommand();
					break;
				case "recommendations":
					recommendationsCommand();
					break;
			}
		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}
	}

	public void useGCMCommand() throws Exception {
		String ac2dmAuth = loginAC2DM();

		MTalkConnector connector = new MTalkConnector(new NotificationListener(service));
		ConnectFuture connectFuture = connector.connect();
		connectFuture.await(TIMEOUT);
		if (!connectFuture.isConnected()) {
			throw new IOException("Couldn't connect to GTALK server!");
		}

		final IoSession session = connectFuture.getSession();
		send(session, IoBuffer.wrap(new byte[] { 0x07 })); // connection sanity check
		System.out.println("Connected to server.");

		String deviceIDStr = String.valueOf(new BigInteger(service.getAndroidID(), 16).longValue());
		String securityTokenStr = String.valueOf(new BigInteger(service.getSecurityToken(), 16).longValue());

		LoginRequestPacket loginRequestPacket = new LoginRequestPacket(deviceIDStr, securityTokenStr, service.getAndroidID());

		LoginResponseFilter loginResponseFilter = new LoginResponseFilter(loginRequestPacket.getPacketID());
		connector.addFilter(loginResponseFilter);
		send(session, loginRequestPacket);
		LoginResponse loginResponse = loginResponseFilter.nextMessage(TIMEOUT);
		connector.removeFilter(loginResponseFilter);
		if(loginResponse==null){
			throw new IllegalStateException("Login response could not be received!");
		} else if(loginResponse.hasError()){
			throw new IllegalStateException(loginResponse.getError().getExtension(0).getMessage());
		}
		System.out.println("Autheticated.");

		BindAccountRequestPacket bindAccountRequestPacket = new BindAccountRequestPacket(service.getEmail(), ac2dmAuth);

		BindAccountResponseFilter barf = new BindAccountResponseFilter(bindAccountRequestPacket.getPacketID());
		connector.addFilter(barf);
		send(session, bindAccountRequestPacket);
		BindAccountResponse bindAccountResponse = barf.nextMessage(TIMEOUT);
		connector.removeFilter(barf);

   /*if(bindAccountResponse==null){
       throw new IllegalStateException("Account bind response could not be received!");
   } else if(bindAccountResponse.hasError()){
       throw new IllegalStateException(bindAccountResponse.getError().getExtension(0).getMessage());
   }*/

		System.out.println("Listening for notifications from server..");

		// send heart beat packets to keep connection up.
		while (true) {
			send(session, new HeartBeatPacket());
			Thread.sleep(30000);
		}
	}

	public static void send(IoSession session, Object object) throws InterruptedException, IOException {
		WriteFuture writeFuture = session.write(object);
		writeFuture.await(TIMEOUT);
		if (!writeFuture.isWritten()) {
			Throwable exception = writeFuture.getException();
			if(exception!=null){
				throw new IOException("Error occured while writing!", exception);
			}
			throw new IOException("Error occured while writing!");
		}
	}

	public ArrayList<String> recommendationsCommand() throws Exception {
		ArrayList<String> resArray = new ArrayList<>();

		login();



		ListResponse recommendations = service.recommendations(packageName, type, offset, number);

		if (recommendations.getDoc(0).getChildCount() == 0) {
			resArray.add("No recommendation found!");
		} else {
			for (DocV2 child : recommendations.getDoc(0).getChildList()) {
				resArray.add(child.getDetails().getAppDetails().getPackageName());
			}
		}
		return resArray;
	}

	public GetReviewsResponse reviewsCommand() throws Exception {
		ArrayList<String> resArray = new ArrayList<>();

		login();

		ReviewResponse reviews = service.reviews(packageName, sort, offset, number);
		GetReviewsResponse response = reviews.getGetResponse();
		if (response.getReviewCount() == 0) {
			resArray.add("No review found!");
		}
		return response;
	}

	public void registerCommand() throws Exception {
		login();
		service.uploadDeviceConfig();
		System.out.println("A device is registered to your account! You can see it at \"https://play.google.com/store/account\" after a few downloads!");
	}

	public ArrayList<String> permissionsCommand() throws Exception {
		ArrayList<String> resArray = new ArrayList<>();

		login();

		List<String> packages = new ArrayList<>();
		packages.add(packageName);
		BulkDetailsResponse bulkDetails = service.bulkDetails(packages);

		for (BulkDetailsEntry bulkDetailsEntry : bulkDetails.getEntryList()) {
			DocV2 doc = bulkDetailsEntry.getDoc();
			AppDetails appDetails = doc.getDetails().getAppDetails();
			resArray.add(doc.getDocid());
			for (String permission : appDetails.getPermissionList()) {
				resArray.add("\t" + permission);
			}
		}
		return resArray;
	}

	public ArrayList<String> searchCommand(String query) throws Exception {
		ArrayList<String> resArray = new ArrayList<>();
		this.query = query;
		login();

		SearchResponse searchResponse = service.search(query, offset, number);
		System.out.println(LIST_HEADER);
		for (DocV2 child : searchResponse.getDoc(0).getChildList()) {
			AppDetails appDetails = child.getDetails().getAppDetails();
			String formatted = new StringJoiner(DELIMETER).add(child.getTitle()).add(appDetails.getPackageName())
					.add(child.getCreator()).add(child.getOffer(0).getFormattedAmount()).add(appDetails.getUploadDate())
					.add(String.valueOf(appDetails.getInstallationSize())).add(appDetails.getNumDownloads()).toString();
			resArray.add(formatted);
		}
		return resArray;
	}

	public ArrayList<String> categoriesCommand() throws Exception {
		ArrayList<String> resArray = new ArrayList<>();
		login();
		BrowseResponse browseResponse = service.browse();
		System.out.println(CATEGORIES_HEADER);
		for (BrowseLink browseLink : browseResponse.getCategoryList()) {
			String[] splitedStrs = browseLink.getDataUrl().split("&cat=");
			resArray.add(new StringJoiner(DELIMETER).add(splitedStrs[splitedStrs.length - 1])
					.add(browseLink.getName()).toString());
		}
		return resArray;
	}

	public ArrayList<String> checkinCommand() throws Exception {
		ArrayList<String> resArray = new ArrayList<>();
		checkin();

		System.out.println("Your account succesfully checkined!");
		resArray.add(service.getAndroidID());
		resArray.add(service.getSecurityToken());
		return resArray;
	}

	public void login() throws Exception {

		if (androidid != null && email != null && password != null) {
			System.out.println("************************ I have reached in Login.....");
			createLoginableService(androidid, email, password, localization);
			service.login();
			return;
		}


		throw new GooglePlayException("Lack of information for login!");
	}

	public String loginAC2DM() throws Exception {

		if (androidid != null && email != null && password != null && securityToken!=null) {
			createLoginableService(androidid, email, password, localization);
			service.login();
			service.setSecurityToken(securityToken);
			return service.loginAC2DM();
		}


		throw new GooglePlayException("Lack of information for login!");
	}

	public void createLoginableService(String androidid, String email, String password, String localization) throws Exception {
		service = new GooglePlayAPI(email, password, androidid);
		service.setLocalization(localization);
		HttpClient proxiedHttpClient = getProxiedHttpClient();
		if (proxiedHttpClient != null) {
			service.setClient(proxiedHttpClient);
		}
	}

	public void createCheckinableService(String email, String password, String localization) throws Exception {
		service = new GooglePlayAPI(email, password);
		service.setLocalization(localization);
		HttpClient proxiedHttpClient = getProxiedHttpClient();
		if (proxiedHttpClient != null) {
			service.setClient(proxiedHttpClient);
		}
	}

	public ArrayList<String> listCommand() throws Exception {
		ArrayList<String> resArray = new ArrayList<>();
		login();

		ListResponse listResponse = service.list(category, subcategory, offset, number);
		if (subcategory == null) {
			resArray.add(SUBCATEGORIES_HEADER);
			for (DocV2 child : listResponse.getDocList()) {
				resArray.add(new StringJoiner(DELIMETER).add(child.getDocid()).add(child.getTitle()).toString());
			}
		} else {
			resArray.add(LIST_HEADER);
			for (DocV2 child : listResponse.getDoc(0).getChildList()) {
				AppDetails appDetails = child.getDetails().getAppDetails();
				resArray.add(new StringJoiner(DELIMETER).add(child.getTitle()).add(appDetails.getPackageName())
						.add(child.getCreator()).add(child.getOffer(0).getFormattedAmount()).add(appDetails.getUploadDate())
						.add(String.valueOf(appDetails.getInstallationSize())).add(appDetails.getNumDownloads())
						.toString());

			}
		}
		return resArray;
	}

	public ArrayList<File> downloadCommand(List<String> packages) throws Exception {
		ArrayList<String> resArray = new ArrayList<>();
		resArray.add("Successfully Downloaded!");
		login();
		ArrayList<File> fileList = new ArrayList<>();
		for(String packageName : packages) {
			fileList.add(download(packageName));
		}
		return fileList;
	}

	public void checkin() throws Exception {


		if (email != null && password != null) {
			createCheckinableService(email, password, localization);
			service.checkin();
			return;
		}

		throw new GooglePlayException("Lack of information for login!");
	}

	public HttpClient getProxiedHttpClient() throws Exception {

		if (host != null && port != null) {
			return getProxiedHttpClient(host, port);
		}

		return null;
	}

	public static HttpClient getProxiedHttpClient(String host, Integer port) throws Exception {
		HttpClient client = new DefaultHttpClient(GooglePlayAPI.getConnectionManager());
		client.getConnectionManager().getSchemeRegistry().register(Utils.getMockedScheme());
		HttpHost proxy = new HttpHost(host, port);
		client.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
		return client;
	}

	public File download(String packageName) throws IOException {
		DetailsResponse details = service.details(packageName);
		AppDetails appDetails = details.getDocV2().getDetails().getAppDetails();
		Offer offer = details.getDocV2().getOffer(0);

		int versionCode = appDetails.getVersionCode();
		long installationSize = appDetails.getInstallationSize();
		int offerType = offer.getOfferType();
		boolean checkoutRequired = offer.getCheckoutFlowRequired();

		// paid application...ignore
		if (checkoutRequired) {
			System.out.println("Checkout required! Ignoring.." + appDetails.getPackageName());
			return null;
		}

		System.out.println("Downloading..." + appDetails.getPackageName() + " : " + installationSize + " bytes");
		InputStream downloadStream = service.download(appDetails.getPackageName(), versionCode, offerType);

		File createApkfile = new File(appDetails.getPackageName() + ".apk");
		FileOutputStream outputStream = new FileOutputStream(createApkfile);

		byte buffer[] = new byte[1024];
		for (int k = 0; (k = downloadStream.read(buffer)) != -1;) {
			outputStream.write(buffer, 0, k);
		}
		downloadStream.close();
		outputStream.close();
		System.out.println("Downloaded! " + appDetails.getPackageName() + ".apk");
		return createApkfile;
	}

}

class ReviewSort implements ArgumentType<Object> {

	@Override
	public Object convert(ArgumentParser parser, Argument arg, String value) throws ArgumentParserException {
		try {
			return REVIEW_SORT.valueOf(value);
		} catch (IllegalArgumentException ex) {
			return value;
		}
	}
}

class ReviewSortChoice extends CollectionArgumentChoice<REVIEW_SORT> {

	public ReviewSortChoice() {
		super(REVIEW_SORT.NEWEST, REVIEW_SORT.HIGHRATING, REVIEW_SORT.HELPFUL);
	}

	@Override
	public boolean contains(Object val) {
		try {
			return super.contains(val);
		} catch (IllegalArgumentException ex) {
			return false;
		}
	}
}

class RecommendationType implements ArgumentType<Object> {

	@Override
	public Object convert(ArgumentParser parser, Argument arg, String value) throws ArgumentParserException {
		try {
			return RECOMMENDATION_TYPE.valueOf(value);
		} catch (IllegalArgumentException ex) {
			return value;
		}
	}
}

class ReleationChoice extends CollectionArgumentChoice<RECOMMENDATION_TYPE> {

	public ReleationChoice() {
		super(RECOMMENDATION_TYPE.ALSO_VIEWED, RECOMMENDATION_TYPE.ALSO_INSTALLED);
	}

	@Override
	public boolean contains(Object val) {
		try {
			return super.contains(val);
		} catch (IllegalArgumentException ex) {
			return false;
		}
	}
}

class StringJoiner {
	private String delimeter;
	List<String> elements = new ArrayList<String>();

	public StringJoiner(String delimeter) {
		this.delimeter = delimeter;
	}

	public StringJoiner add(String elem) {
		elements.add(elem);
		return this;
	}

	@Override
	public String toString() {
		if (elements.isEmpty())
			return "";
		Iterator<String> iter = elements.iterator();
		StringBuilder builder = new StringBuilder(iter.next());
		while (iter.hasNext()) {
			builder.append(delimeter).append(iter.next());
		}
		return builder.toString();
	}
}

class LoginResponseFilter extends MessageFilter<LoginResponse>{
	private String id;

	public LoginResponseFilter(String id) {
		super(LoginResponse.class);
		this.id = id;
	}

	@Override
	protected boolean accept(LoginResponse message) {
		return id.equals(message.getPacketid());
	}
}

class BindAccountResponseFilter extends MessageFilter<BindAccountResponse>{
	private String id;

	public BindAccountResponseFilter(String id) {
		super(BindAccountResponse.class);
		this.id = id;
	}

	@Override
	protected boolean accept(BindAccountResponse message) {
		return id.equals(message.getPacketid());
	}
}