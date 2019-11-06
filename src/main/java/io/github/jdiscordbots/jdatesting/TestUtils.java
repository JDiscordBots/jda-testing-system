/*
 * Copyright (c) JDiscordBots 2019
 * File: TestUtils.java
 * Project: jda-testing-system
 * Licensed under Boost Software License 1.0
 */
package io.github.jdiscordbots.jdatesting;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Predicate;

import org.awaitility.Awaitility;
import org.awaitility.Durations;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.internal.entities.SelfUserImpl;

/**
 * various utilities for JDA feature tests<br>
 * requires a file named <code>jda-tests.properties</code> in the root of the classpath<br>
 * This file needs to be structured like this: <code>&lt;key&gt;=&lt;value&gt;</code><br>
 * It should contain values for the following keys 
 * <ul>
 * 	<li><i>jda-factory-class</i> the fully qualified name of the class where the <i>jda-factory-method</i> is located</li>
 * 	<li><i>jda-factory-method</i> the name of the method that loads and returns the {@link JDA} Object</li>
 * 	<li><i>testing-channel</i> the Discord Text Channel where commands should be tested</li>
 * 	<li><i>testing-prefix</i> the command-prefix of the bot to test</li>
 * </ul>
 */
public final class TestUtils {
	
	private static JDA jda=null;
	private static Properties props=new Properties();
	private static OffsetDateTime start=Instant.now().atOffset(ZoneOffset.UTC);
	
	static{
		try {
			props.load(TestUtils.class.getClassLoader().getResourceAsStream("jda-tests.properties"));
			String jdaGetterClassName = props.getProperty("jda-factory-class");
			String jdaGetterMethodName=props.getProperty("jda-factory-method");
			Class<?> factoryClass = Class.forName(jdaGetterClassName);
			Method factoryMethod = factoryClass.getMethod(jdaGetterMethodName);
			jda = (JDA) factoryMethod.invoke(null);
			SecurityManager oldSecManager=System.getSecurityManager();
			System.setSecurityManager(new SecurityManager() {
				@Override
				public void checkPermission(Permission perm) {
					if(oldSecManager!=null) {
						oldSecManager.checkPermission(perm);
					}
				}
				@Override
				public void checkPermission(Permission perm, Object context) {
					if(oldSecManager!=null) {
						oldSecManager.checkPermission(perm,context);
					}
				}
				@Override
				public void checkExit(int status) {
					super.checkExit(status);
					boolean goOn=true;
					while(goOn) {
						for(Message msg:getTestingChannel().getHistory().retrievePast(100).complete()) {
							if(isMessageSentDuringTest(msg)) {
								if(msg.getAuthor().equals(jda.getSelfUser())) {
									msg.delete().complete();
								}
							}else {
								goOn=false;
								break;
							}
						}
					}
					jda.shutdown();
				}
			});
			((SelfUserImpl)jda.getSelfUser()).setBot(false);
		} catch (IOException | ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}
	private TestUtils() {
		//prevent instantiation
	}
	private static boolean isMessageSentDuringTest(Message msg) {
		return msg.getTimeCreated().isAfter(start);
	}
	/**
	 * gets the {@link JDA} instance returned by the <i>jda-factory-method</i>
	 * @return the JDA Object
	 */
	public static JDA getJDA() {
		return jda;
	}
	/**
	 * gets the Discord {@link TextChannel} Object that belongs to a certain Snowflake
	 * @param id the Snowflake ID
	 * @return the TextChannel
	 */
	public static TextChannel getChannel(String id) {
		return jda.getTextChannelById(id);
	}
	/**
	 * gets the Discord {@link Guild} Object that belongs to a certain Snowflake
	 * @param id the Snowflake ID
	 * @return the Guild
	 */
	public static Guild getGuild(String id) {
		return jda.getGuildById(id);
	}
	/**
	 * gets the Discord {@link Role} Object that belongs to a certain Snowflake
	 * @param id the Snowflake ID
	 * @return the Role
	 */
	public static Role getRole(String id) {
		return jda.getRoleById(id);
	}
	/**
	 * gets the Discord {@link User} Object that belongs to a certain Snowflake
	 * @param id the Snowflake ID
	 * @return the User
	 */
	public static User getUser(String id) {
		return jda.getUserById(id);
	}
	/**
	 * gets a {@link Message} that fulfills certain criteria has already been sent but was sent during the tests
	 * @param tc the {@link TextChannel} where the message was sent
	 * @param tester a function that returns <code>true</code> if a message is the correct message
	 * @return the {@link Message} Object or null if it hasn't been sent
	 */
	public static Message getAlreadySentMessage(TextChannel tc,Predicate<Message> tester) {
		for (Message msg : tc.getHistory().retrievePast(100).complete()) {
			
			if(!isMessageSentDuringTest(msg)) {
				return null;
			}
			if (tester.test(msg)) {
				return msg;
			}
		}
		return null;
	}
	private static final class Wrapper<T>{
		T data;
	}
	/**
	 * gets a {@link Message} in a {@link TextChannel} that contains a String that may not be already sent(and received) but was/will be sent during the tests
	 * @param tc the {@link TextChannel}
	 * @param s the String
	 * @return the {@link Message}
	 */
	public static Message getMessage(TextChannel tc,String s) {
		return getMessage(tc,msg->msg.getContentRaw().equals(s));
	}
	/**
	 * gets a {@link Message} in the testing channel that contains a String that may not be already sent(and received) but was/will be sent during the tests
	 * @param s the String
	 * @return the {@link Message}
	 * @see TestUtils#getTestingChannel()
	 */
	public static Message getMessage(String s) {
		return getMessage(getTestingChannel(),msg->msg.getContentRaw().equals(s));
	}
	/**
	 * gets a {@link Message} in a {@link TextChannel} that fulfills certain criteria that may not be already sent(and received) but was/will be sent during the tests
	 * @param tc the {@link TextChannel}
	 * @param tester a function that returns <code>true</code> if a message is the correct message
	 * @return the {@link Message}
	 */
	public static Message getMessage(TextChannel tc,Predicate<Message> tester) {
		Wrapper<Message> msg=new Wrapper<>();
		Awaitility.await().atMost(Durations.FIVE_SECONDS).until(()->(msg.data=getAlreadySentMessage(tc,tester))!=null);
		return msg.data;
	}
	/**
	 * gets a {@link Message} in the testing channel that fulfills certain criteria that may not be already sent(and received) but was/will be sent during the tests
	 * @param tester a function that returns <code>true</code> if a message is the correct message
	 * @return the {@link Message}
	 * @see TestUtils#getTestingChannel()
	 */
	public static Message getMessage(Predicate<Message> tester) {
		return getMessage(getTestingChannel(),tester);
	}
	/**
	 * gets a {@link Message} in a {@link TextChannel} that was sent by a certain user that may not be already sent(and received) but was/will be sent during the tests
	 * @param tc the {@link TextChannel}
	 * @param member the user
	 * @return the {@link Message}
	 */
	public static Message getMessage(TextChannel tc,Member member) {
		return getMessage(tc, msg->msg.getMember().equals(member));
	}
	/**
	 * gets a {@link Message} in the testing channel was sent by a certain user that may not be already sent(and received) but was/will be sent during the tests
	 * @param member the user
	 * @return the {@link Message}
	 * @see TestUtils#getTestingChannel()
	 */
	public static Message getMessage(Member member) {
		return getMessage(getTestingChannel(), member);
	}
	/**
	 * gets a {@link Message} in a {@link TextChannel} that may not be already sent(and received) but was/will be sent during the tests
	 * @param tc the {@link TextChannel}
	 * @return the {@link Message}
	 */
	public static Message getMessage(TextChannel tc) {
		return getMessage(tc, msg->true);
	}
	/**
	 * gets a {@link Message} in the testing channel that may not be already sent(and received) but was/will be sent during the tests
	 * @param tc the {@link TextChannel}
	 * @return the {@link Message}
	 * @see TestUtils#getTestingChannel()
	 */
	public static Message getMessage() {
		return getMessage(getTestingChannel(), msg->true);
	}
	/**
	 * sends a message in a {@link TextChannel} and waits until it has been sent
	 * @param message the content of the message
	 * @param tc the {@link TextChannel} where the message should be sent
	 */
	public static void sendMessage(String message,TextChannel tc) {
		tc.sendMessage(message).complete();
	}
	/**
	 * sends a message in the testing channel and waits until it has been sent
	 * @param message the content of the message
	 * @param tc the {@link TextChannel} where the message should be sent
	 * @see TestUtils#getTestingChannel()
	 */
	public static void sendMessage(String message) {
		sendMessage(message, getTestingChannel());
	}
	/**
	 * sends a command for the bot to test in the testing channel<br>
	 * This sends a message beginning with the prefix specified in the <code>jda-tests.properties</code>
	 * @param content the content that should be added to the message after the prefix
	 * @see TestUtils#getTestingChannel()
	 */
	public static void sendCommand(String content) {
		sendMessage(getPrefix()+content);
	}
	/**
	 * sends a command for the bot to test in a {@link TextChannel}<br>
	 * This sends a message beginning with the prefix specified in the <code>jda-tests.properties</code>
	 * @param content the content that should be added to the message after the prefix
	 * @param tc the {@link TextChannel} where the command should be sent
	 */
	public static void sendCommand(String content,TextChannel tc) {
		sendMessage(getPrefix()+content,tc);
	}
	/**
	 * gets the testing channel specified in the <code>jda-tests.properties</code><br>
	 * The key is named <code>testing-channel</code>
	 * @return the testing channel as {@link TextChannel}
	 */
	public static TextChannel getTestingChannel() {
		return getChannel(props.getProperty("testing-channel"));
	}
	/**
	 * gets the prefix specified in the <code>jda-tests.properties</code><br>
	 * The key is named <code>testing-prefix</code>
	 * @return the prefix
	 */
	public static String getPrefix() {
		return props.getProperty("testing-prefix");
	}
	/**
	 * tests if a {@link Message} has an Embed that fulfills a certain criteria
	 * @param msg the {@link Message}
	 * @param tester a function that returns <code>true</code> if an embed fulfills the criteria
	 * @return <code>true</code> if the message contains an embed that fulfills the criteria, else <code>false</code>
	 */
	public static boolean hasEmbed(Message msg,Predicate<MessageEmbed> tester){
		for (MessageEmbed embed : msg.getEmbeds()) {
			if(tester.test(embed)) {
				return true;
			}
		}
		return false;
	}
	/**
	 * tests if a {@link Message} has an Embed that has a specified title and description
	 * @param msg the {@link Message}
	 * @param title the specified title
	 * @param description the specified description
	 * @return<code>true</code> if the message contains an embed with the title and description, else <code>false</code>
	 */
	public static boolean hasEmbed(Message msg,String title,String description) {
		return hasEmbed(msg,embed->Objects.equals(embed.getTitle(),title)&&Objects.equals(embed.getDescription(),description));
	}
	/**
	 * tests if an embed contains a field that fulfills a certain criteria
	 * @param embed the embed as {@link MessageEmbed} Object
	 * @param tester a function that returns <true> if a field fulfills the criteria
	 * @return <code>true</code> if the embed contains such a field, else <code>false</code>
	 */
	public static boolean hasEmbedField(MessageEmbed embed,Predicate<Field> tester) {
		for (Field field : embed.getFields()) {
			if(tester.test(field)) {
				return true;
			}
		}
		return false;
	}
	/**
	 * tests if an embed contains a field with a specified name and value
	 * @param embed the embed as {@link MessageEmbed} Object
	 * @param title the name
	 * @param content the value
	 * @return <code>true</code> if the embed contains such a field, else <code>false</code>
	 */
	public static boolean hasEmbedField(MessageEmbed embed,String title,String content){
		return hasEmbedField(embed,field->field.getName().equals(title)&&field.getValue().equals(content));
	}
	/**
	 * tests if a message contains an embed with a field that fulfills a certain criteria
	 * @param msg the {@link Message}
	 * @param tester a function that returns <true> if a field fulfills the criteria
	 * @return <code>true</code> if the embed contains such a field, else <code>false</code>
	 */
	public static boolean hasEmbedField(Message msg,Predicate<Field> tester){
		return hasEmbed(msg,embed->hasEmbedField(embed,tester));
	}
	/**
	 * tests if a message contains an embed with a field with a specified name and value
	 * @param msg the {@link Message}
	 * @param title the name
	 * @param content the value
	 * @return <code>true</code> if the message contains such an embed, else <code>false</code>
	 */
	public static boolean hasEmbedField(Message msg,String title,String content){
		return hasEmbedField(msg,field->field.getName().equals(title)&&field.getValue().equals(content));
	}
	/**
	 * invokes a method that is private using reflection
	 * @param targetClass the Class, the method is defined in
	 * @param targetMethodName the name of the method to be invoked
	 * @param paramTypes the parameter types of the method to be invoked
	 * @param instanceOfClass the instance of the class where the method is in, or <code>null</code> if the method is <code>static</code>
	 * @param params the arguments to pass to the method
	 * @return the return value of the method, or <code>null</code> if it doesn't have a return type
	 * @throws IllegalAccessException if the method is not accessible
	 * @throws InvocationTargetException if the method threw any Exceptions
	 * @throws NoSuchMethodException if there is no method with the given name in the given class
	 */
	public static Object invokeNotAccessibleMethod(Class<?> targetClass,String targetMethodName,Class<?>[] paramTypes, Object instanceOfClass, Object... params) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		Method method = targetClass.getDeclaredMethod(targetMethodName, paramTypes);
		AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
			method.setAccessible(true);
			return null;
		});
		return method.invoke(instanceOfClass, params);
	}
}
