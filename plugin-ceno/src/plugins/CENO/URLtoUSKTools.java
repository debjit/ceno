package plugins.CENO;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.validator.routines.InetAddressValidator;
import org.apache.commons.validator.routines.UrlValidator;

import freenet.client.InsertException;
import freenet.keys.FreenetURI;

public class URLtoUSKTools {

	public static Map<String, String> splitURL(String requestPath) throws MalformedURLException {
		// Extract domain and extra path
		String domain, extraPath;
		int slashIndex = requestPath.indexOf('/');
		// Support for URLs with queries that follow right after <host> without a slash /
		int queryIndex = requestPath.indexOf('?');
		if (queryIndex > -1 && queryIndex < slashIndex) {
			slashIndex = queryIndex;
		}
		if (slashIndex < 1 || slashIndex == requestPath.length()) {
			domain = requestPath;
			extraPath = "";
		} else {
			domain = requestPath.substring(0, slashIndex);
			extraPath = requestPath.substring(slashIndex + 1, requestPath.length());
		}

		/* Extract meta strings from FreenetURI
		StringBuilder allMetaStrings = new StringBuilder();
		for (String metaString : requestKey.getAllMetaStrings()) {
			if (!metaString.isEmpty()) {
				allMetaStrings.append("/" + metaString);
			}
		}*/	

		Map<String, String> splitMap = new HashMap<String, String>();
		splitMap.put("domain", domain);
		splitMap.put("extraPath", extraPath);
		return splitMap;
	}

	/**
	 * Computes the USK for a given URL so that:
	 * <ul>
	 *   <li> CENO can lookup if this URL has been cached before</li>
	 *   <li> CENO knows the insert URI to use when caching a bundle</li>
	 * </ul>
	 * 
	 * @param requestPath the URL requested by the user/bundler
	 * @return the calculated FreenetURI that corresponds to that resource
	 * @throws MalformedURLException
	 */
	public static FreenetURI computeUSKfromURL(String requestPath, String requestURI) throws MalformedURLException {
		Map<String, String> splitMap = splitURL(requestPath);
		String computedKey = requestURI.replaceFirst("SSK", "USK") + splitMap.get("domain") + "/0/" + splitMap.get("extraPath");

		return new FreenetURI(computedKey);
	}

	public static FreenetURI computeInsertURI(String domain, String insertURI) throws MalformedURLException {
		FreenetURI insertURIconfig = new FreenetURI(insertURI);
		FreenetURI result = new FreenetURI("USK", domain, insertURIconfig.getRoutingKey(), insertURIconfig.getCryptoKey(), insertURIconfig.getExtra());

		try {
			result.checkInsertURI();
		} catch (InsertException e) {
			throw new MalformedURLException("The computed insertion key for domain " + domain + " failed validity check");
		}
		return result;
	}

	/**
	 * Validates a URL parameter and formats it accordingly
	 * 
	 * @param urlParam the URL to validate and format
	 * @return the URL in format that can be processed by the CENO plugins
	 * @throws MalformedURLException if URL parameter is not a valid URL
	 */
	public static String validateURL(String urlParam) throws MalformedURLException {
		// Remove preceding slash
		urlParam = urlParam.replaceFirst("^/", "");

		// Remove the preceding "?url="
		urlParam = urlParam.replaceFirst("(?i)^?url=", "");

		// Remove the http/https protocols
		urlParam = urlParam.replaceFirst("(?i)^https?://", "");

		// Make sure that only http/https is allowed
		if (Pattern.matches(".*://.*", urlParam)) {
			throw new MalformedURLException("Unsupported protocol");
		}

		// Check if urlParam is a Freenet key
		if (Pattern.matches("(?i)^(freenet:|USK@|CHK@|SSK@).*", urlParam)) {
			throw new MalformedURLException("Given URL looks like a Freenet key");
		}

		// Throws MalformedURLException if a URL object cannot be created
		URL netURL = new URL("http://" + urlParam);

		// Validate urlParm using org.apache.commons UrlValidator
		UrlValidator urlValidator = new UrlValidator();
		if (!urlValidator.isValid(netURL.toString())) {
			throw new MalformedURLException("Given URL failed UrlValidator check");
		}

		// Make sure the URL host is not localhost
		if (netURL.getHost().equals("localhost")) {
			throw new MalformedURLException("Given URL references a local resource");
		}

		// If the host is an Inet address, make sure it does not reference to a local resource
		InetAddressValidator inetValidator = new InetAddressValidator();
		if (inetValidator.isValid(netURL.getHost())) {
			InetAddress inet;
			try {
				// !IMPORTANT!
				// Make sure that URL host is in IPv4 or IPv6 format before calling InetAddress.getByName()
				// because in case the host is a domain, it will be resolved and
				// DNS resolution can expose the identity of the users.
				inet = InetAddress.getByName(netURL.getHost());
			} catch (UnknownHostException e) {
				// Since we have already checked that the urlParam host is a valid Inet address,
				// this UnknownHost exception will never be thrown
				e.printStackTrace();
				throw new MalformedURLException();
			}
			if (inet.isSiteLocalAddress()) {
				throw new MalformedURLException("Given URL references a local resource");
			}
		}

		return urlParam;
	}

}