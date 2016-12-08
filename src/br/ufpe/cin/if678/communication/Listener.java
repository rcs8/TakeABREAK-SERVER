package br.ufpe.cin.if678.communication;

import java.net.InetSocketAddress;
import java.util.Map;

import br.ufpe.cin.if678.ServerController;
import br.ufpe.cin.if678.business.Group;
import br.ufpe.cin.if678.util.Pair;
import br.ufpe.cin.if678.util.Tuple;

public class Listener {

	private static int nextID = 1;

	private ServerController controller;

	public Listener(ServerController controller) {
		this.controller = controller;
	}

	public void onUserConnect(String username, InetSocketAddress address) {
		int ID;

		if (controller.getNameToID().containsKey(username) && controller.isOnline(controller.getNameToID().get(username))) {
			controller.getWriter(address).queueAction(ServerAction.VERIFY_USERNAME, -1);
			return;
		} else if (controller.getNameToID().containsKey(username)) {
			ID = controller.getNameToID().get(username);
		} else {
			ID = nextID++;
		}

		controller.getWriter(address).queueAction(ServerAction.VERIFY_USERNAME, ID);

		controller.getAddressToID().put(address, ID);
		controller.getNameToID().put(username, ID);
		controller.getIDToNameAddress().put(ID, new Pair<String, InetSocketAddress>(username, address));

		Tuple<Integer, String, InetSocketAddress> data = new Tuple<Integer, String, InetSocketAddress>(ID, username, address);
		for (Map.Entry<InetSocketAddress, Pair<Writer, Thread>> entry : controller.getWriters()) {
			InetSocketAddress userAddress = entry.getKey();
			Writer writer = entry.getValue().getFirst();

			if (userAddress != address) {
				writer.queueAction(ServerAction.USER_CONNECTED, data);
			}
		}

		System.out.println("[LOG] USUÁRIO CONECTOU:    <" + ID + ", " + username + ", " + controller.getAddressPort(address) + ">");
	}

	public void onUserListRequest(InetSocketAddress address) {
		controller.getWriter(address).queueAction(ServerAction.USERS_LIST_UPDATE, controller.getIDToNameAddress());
	}

	public void onGroupCreate(Pair<Integer, String> data) {
		int founder = data.getFirst();
		String name = data.getSecond();

		Group group = controller.getGroupManager().getGroup(name);

		if (group == null) {
			group = controller.getGroupManager().createGroup(founder, name);
		}

		controller.getWriter(founder).queueAction(ServerAction.SEND_GROUP, group);
	}

	public void onGroupAddMember(Pair<String, Integer> data) {
		String name = data.getFirst();
		Integer user = data.getSecond();

		Group group = controller.getGroupManager().getGroup(name);
		if (!group.isMember(user)) {
			group.addMember(user);
		}

		controller.getWriter(group.getFounderID()).queueAction(ServerAction.GROUP_ADD_MEMBER, group);
		for (int member : group.getMembers().keySet()) {
			controller.getWriter(member).queueAction(ServerAction.GROUP_ADD_MEMBER, group);
		}
	}

	public void onGroupMessage(Tuple<String, Integer, Object> tuple) {
		String name = tuple.getFirst();

		Group group = controller.getGroupManager().getGroup(name);

		controller.getWriter(group.getFounderID()).queueAction(ServerAction.GROUP_MESSAGE, tuple);
		for (int member : group.getMembers().keySet()) {
			controller.getWriter(member).queueAction(ServerAction.GROUP_MESSAGE, tuple);
		}
	}

	public void onReconnect(Tuple<Integer, String, InetSocketAddress> data) {
		int ID = data.getFirst();
		String username = data.getSecond();
		InetSocketAddress address = data.getThird();

		controller.getAddressToID().put(address, ID);
		controller.getNameToID().put(username, ID);
		controller.getIDToNameAddress().put(ID, new Pair<String, InetSocketAddress>(username, address));

		for (Map.Entry<InetSocketAddress, Pair<Writer, Thread>> entry : controller.getWriters()) {
			controller.getWriter(entry.getKey()).queueAction(ServerAction.USERS_LIST_UPDATE, controller.getIDToNameAddress().clone());
		}
	}

}
