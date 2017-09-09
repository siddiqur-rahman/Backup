
/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.NameValuePair;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

import com.fasterxml.jackson.databind.ObjectMapper;

class MsgObj {
	 int status = -1;
	 static MsgObj msgObj = null;
	private MsgObj(){
		this.status = -1;
	}
	public int getStatus () {
		return this.status;
	}
	public static MsgObj getMsgObj (){
		if(msgObj == null){
			msgObj = new MsgObj();
		} 
		return msgObj;
	}
}

public class QuickStart {

	/*
	 * result code meaning: 3 --- wrong password 4 --- network timeout
	 * 
	 * 
	 */
	public static boolean login(String password, CloseableHttpClient httpclient) throws Exception {
		HttpPost httpPost = new HttpPost("http://192.168.0.1/goform/goform_set_cmd_process");
		httpPost.setHeader("Referer", "http://192.168.0.1/index.html");
		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("isTest", "false"));
		nvps.add(new BasicNameValuePair("goformId", "LOGIN"));
		nvps.add(new BasicNameValuePair("password", Base64.getEncoder().encodeToString(password.getBytes())));
		httpPost.setEntity(new UrlEncodedFormEntity(nvps));
		CloseableHttpResponse response = httpclient.execute(httpPost);
		ResponseHandler<String> handler = new BasicResponseHandler();
		String body = handler.handleResponse(response);
		System.out.println(body);
		return body.matches("\\{.result.*0.\\}"); // 3 means wrong password
	}

	public static boolean sendUssd(String ussd, CloseableHttpClient httpclient) throws Exception {
		HttpPost httpPost = new HttpPost("http://192.168.0.1/goform/goform_set_cmd_process");
		httpPost.setHeader("Referer", "http://192.168.0.1/index.html");
		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("isTest", "false"));
		nvps.add(new BasicNameValuePair("goformId", "USSD_PROCESS"));
		nvps.add(new BasicNameValuePair("USSD_operator", "ussd_send"));
		nvps.add(new BasicNameValuePair("USSD_send_number", ussd));
		nvps.add(new BasicNameValuePair("notCallback", "true"));

		httpPost.setEntity(new UrlEncodedFormEntity(nvps));
		CloseableHttpResponse response = httpclient.execute(httpPost);
		ResponseHandler<String> handler = new BasicResponseHandler();
		String body = handler.handleResponse(response);
		System.out.println(body);
		return body.matches("\\{.result.*success.\\}");
	}

	public static int getUssdRequestStatus(CloseableHttpClient httpclient) throws Exception {
		// need to send request after 1 second avg. if we don't get reading flag
		// status code 15 means sending (so now busy)
		// status 4 means network timeout
		// status 16 means data is ready

		final String regex = "\\{\"ussd_write_flag\":\"(\\d+)\"\\}";
		int flagValue = -1;
		String currentMilliSecond = Long.toString(Calendar.getInstance().getTimeInMillis());
		HttpGet httpGet = new HttpGet(
				"http://192.168.0.1/goform/goform_get_cmd_process?cmd=ussd_write_flag&_=" + currentMilliSecond);
		httpGet.setHeader("Referer", "http://192.168.0.1/index.html");
		CloseableHttpResponse response = httpclient.execute(httpGet);
		ResponseHandler<String> handler = new BasicResponseHandler();
		String body = handler.handleResponse(response);
		System.out.println(body);
		
		// {"ussd_write_flag":"15"}
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(body);
		
		if (matcher.find()) {
			flagValue = Integer.valueOf(matcher.group(1));
		}

		return flagValue;
	}

	public static String getUssdData(CloseableHttpClient httpclient) throws Exception {
		String currentMilliSecond = Long.toString(Calendar.getInstance().getTimeInMillis());
		HttpGet httpGet = new HttpGet("http://192.168.0.1/goform/goform_get_cmd_process?cmd=ussd_data_info&_=" + currentMilliSecond);
		httpGet.setHeader("Referer", "http://192.168.0.1/index.html");
		CloseableHttpResponse response = httpclient.execute(httpGet);
		ResponseHandler<String> handler = new BasicResponseHandler();
		String body = handler.handleResponse(response);
		System.out.println(body);
		// {"ussd_action":"1","ussd_dcs":"72","ussd_data":"004D00610069006E0020004D0065006E0075000A000A0031002E0020005000750072006300680061007300650020006100200070006C0061006E000A0032002E0020004D0061006E0061006700650020006500780069007300740069006E006700200070006C0061006E002800730029000A0033002E002000530068006100720065004D0079004E00650074002000670072006F00750070000A0034002E0020004E006F00740069006600690063006100740069006F006E002000730065007400740069006E00670073000A0035002E00200042006F006F00730074006500720073000A0036002E00200045007800690074"}
		ObjectMapper mapper = new ObjectMapper();
		Map<String, String> dataMap = mapper.readValue(body, Map.class);
		StringBuilder output = new StringBuilder();
		String hexData = dataMap.get("ussd_data");

		for (int i = 0; i < hexData.length() - 4; i += 4) {
			String str = hexData.substring(i + 2, i + 4);
			output.append((char) Integer.parseInt(str, 16));
		}
		return output.toString();
	}

	public static void sendUssdReply(CloseableHttpClient httpclient, String ussdReplyNumber) throws Exception {
		HttpPost httpPost = new HttpPost("http://192.168.0.1/goform/goform_set_cmd_process");
		httpPost.setHeader("Referer", "http://192.168.0.1/index.html");
		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("isTest", "false"));
		nvps.add(new BasicNameValuePair("goformId", "USSD_PROCESS"));
		nvps.add(new BasicNameValuePair("USSD_operator", "ussd_reply"));
		nvps.add(new BasicNameValuePair("USSD_reply_number", ussdReplyNumber));
		nvps.add(new BasicNameValuePair("notCallback", "true"));
		httpPost.setEntity(new UrlEncodedFormEntity(nvps));
		CloseableHttpResponse response = httpclient.execute(httpPost);
		ResponseHandler<String> handler = new BasicResponseHandler();
		String body = handler.handleResponse(response);
		System.out.println(body);
	}

	public static boolean sendCancelUssdOperation(CloseableHttpClient httpclient) throws Exception {
		String currentMilliSecond = Long.toString(Calendar.getInstance().getTimeInMillis());
		HttpGet httpGet = new HttpGet("http://192.168.0.1/goform/goform_set_cmd_process?goformId=USSD_PROCESS&USSD_operator=ussd_cancel&_=" + currentMilliSecond);
		httpGet.setHeader("Referer", "http://192.168.0.1/index.html");
		CloseableHttpResponse response = httpclient.execute(httpGet);
		ResponseHandler<String> handler = new BasicResponseHandler();
		String body = handler.handleResponse(response);
		System.out.println(body);
		return body.matches("\\{.result.*success.\\}");
	}

	public static boolean canReadUssdData(CloseableHttpClient httpclient, int successCode, int errorCode) throws IOException {
		System.out.println("checking data availability");
		try {
			while ( ((MsgObj.getMsgObj().status = getUssdRequestStatus(httpclient)) != successCode) && MsgObj.getMsgObj().status != errorCode) {
				System.out.println(": got status : " + MsgObj.getMsgObj().status);
				Thread.sleep(1000);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		System.out.println("final status code : " + MsgObj.getMsgObj().status);
		return successCode == MsgObj.getMsgObj().status;
	}
	
	public static void getUnseenMsg(CloseableHttpClient httpclient) throws Exception {
		String currentMilliSecond = Long.toString(Calendar.getInstance().getTimeInMillis());
		HttpGet httpGet = new HttpGet("http://192.168.0.1/goform/goform_get_cmd_process?isTest=false&cmd=sms_data_total&page=0&data_per_page=5&mem_store=10&tags=1&order_by=order+by+id+desc&_=" + currentMilliSecond);
		httpGet.setHeader("Referer", "http://192.168.0.1/index.html");
		CloseableHttpResponse response = httpclient.execute(httpGet);
		ResponseHandler<String> handler = new BasicResponseHandler();
		String body = handler.handleResponse(response);
		System.out.println(body);
		ObjectMapper mapper = new ObjectMapper();
		Map<String, String> dataMap = mapper.readValue(body, Map.class);
		System.out.println(dataMap);
	}
	
	public static void main(String[] args) throws Exception {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		
		int retry = 1;
		int maxRetry = 4;

		getUnseenMsg(httpclient);
		try {
			while (retry < maxRetry) {
				retry ++;
				boolean login = false;
				if (login || (login = login("admin", httpclient))) {
					System.out.println(" successfully logged in.");
					System.out.println("---now testing ussd---");

					if (sendUssd("*247#", httpclient)) {
						System.out.println("ussd running successful");
						System.out.println("now checking ussd status code");
						int statusCode = 1;
//
//						int statusCode = getUssdRequestStatus(httpclient);
//						while (statusCode == 15) {
//							Thread.sleep(1000);
//							statusCode = getUssdRequestStatus(httpclient);
//						}

						//if (statusCode == 16) {
						if (canReadUssdData(httpclient, 16, 4)) {
							System.out.println("Reading ussd data");
							String data = getUssdData(httpclient);
							System.out.println(data);

							Scanner in = new Scanner(System.in);
							String option = in.nextLine();
							System.out.println(option);
							sendUssdReply(httpclient, option);

							while (!in.equals("-11")) {
								while ((statusCode = getUssdRequestStatus(httpclient)) == 15) {
									Thread.sleep(1000);
								}
								if (statusCode == 16) {
									System.out.println("Reading ussd data");
									data = getUssdData(httpclient);
									System.out.println(data);
									option = in.nextLine();
									getUnseenMsg(httpclient);
									sendUssdReply(httpclient, option);
									statusCode = 15;
								} else if (statusCode == 4) {
									System.out.println("network error");
									break;
								}
								System.out.println("you can cancel by typing -11");
							}
						}
					}
				}
				System.out.println("Retrying .....");
			}
		} finally {
			httpclient.close();
		}
	}

}
