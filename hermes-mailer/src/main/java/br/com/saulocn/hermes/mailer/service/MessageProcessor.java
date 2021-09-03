package br.com.saulocn.hermes.mailer.service;

import org.apache.pulsar.client.api.Message;

public class MessageProcessor {

	public static void process(Message message) {
		try {
			Thread.sleep(1000);
			System.out.println(String.format("Message received on mail: %s", new String(message.getData())));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
