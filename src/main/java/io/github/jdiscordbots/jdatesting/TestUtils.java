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
	public static JDA getJDA() {
		return jda;
	}
	public static TextChannel getChannel(String id) {
		return jda.getTextChannelById(id);
	}
	public static Guild getGuild(String id) {
		return jda.getGuildById(id);
	}
	public static Role getRole(String id) {
		return jda.getRoleById(id);
	}
	public static User getUser(String id) {
		return jda.getUserById(id);
	}
	public static Message getAlreadySentMessage(TextChannel tc,Predicate<Message> s) {
		for (Message msg : tc.getHistory().retrievePast(100).complete()) {
			
			if(!isMessageSentDuringTest(msg)) {
				return null;
			}
			if (s.test(msg)) {
				return msg;
			}
		}
		return null;
	}
	private static final class Wrapper<T>{
		T data;
	}
	public static Message getMessage(TextChannel tc,Predicate<Message> s) {
		Wrapper<Message> msg=new Wrapper<>();
		Awaitility.await().atMost(Durations.FIVE_SECONDS).until(()->(msg.data=getAlreadySentMessage(tc,s))!=null);
		return msg.data;
	}
	public static Message getMessage(TextChannel tc,Member member) {
		return getMessage(tc, msg->msg.getMember().equals(member));
	}
	public static void sendMessage(String message,TextChannel tc) {
		tc.sendMessage(message).complete();
	}
	public static void sendMessage(String message) {
		sendMessage(message, getTestingChannel());
	}
	public static void sendCommand(String content) {
		sendMessage(getPrefix()+content);
	}
	public static void sendCommand(String content,TextChannel tc) {
		sendMessage(getPrefix()+content,tc);
	}
	public static TextChannel getTestingChannel() {
		return getChannel(props.getProperty("testing-channel"));
	}
	public static String getPrefix() {
		return props.getProperty("testing-prefix");
	}
	
	public static boolean hasEmbed(Message msg,Predicate<MessageEmbed> tester){
		for (MessageEmbed embed : msg.getEmbeds()) {
			if(tester.test(embed)) {
				return true;
			}
		}
		return false;
	}
	public static boolean hasEmbed(Message msg,String title,String description) {
		return hasEmbed(msg,embed->embed.getTitle().equals(title)&&embed.getDescription().equals(description));
	}
	public static boolean hasEmbedField(Message msg,Predicate<Field> tester){
		return hasEmbed(msg,embed->{
			for (Field field : embed.getFields()) {
				if(tester.test(field)) {
					return true;
				}
			}
			return false;
		});
	}
	public static boolean hasEmbedField(Message msg,String title,String content){
		return hasEmbedField(msg,field->field.getName().equals(title)&&field.getValue().equals(content));
	}
	
	public static Object invokeNotAccessibleMethod(Class<?> targetClass,String targetMethodName,Class<?>[] paramTypes, Object instanceOfClass, Object... params) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		Method method = targetClass.getDeclaredMethod(targetMethodName, paramTypes);
		AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
			method.setAccessible(true);
			return null;
		});
		return method.invoke(instanceOfClass, params);
	}
}
