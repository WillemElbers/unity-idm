/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.webui.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.event.Action;
import com.vaadin.ui.Label;

/**
 * 1-column table with arbitrary objects. 
 * Allows for sorting and default disable multiselect, uses {@link BeanItemContainer}.
 * The value is obtained either via toString() method of the content item or via a given implementation 
 * of {@link NameProvider}.
 * @author K. Benedyczak
 */
public class GenericElementsTable<T> extends SmallTable
{
	private NameProvider<T> nameProvider;
	private List<SingleActionHandler> actionHandlers;
	
	
	public GenericElementsTable(String columnHeader)
	{
		this(columnHeader, new DefaultNameProvider<T>());
	}
	
	public GenericElementsTable(String columnHeader, NameProvider<T> nameProvider)
	{
		this.nameProvider = nameProvider;
		this.actionHandlers = new ArrayList<>();
		setNullSelectionAllowed(false);
		setImmediate(true);
		setSizeFull();
		BeanItemContainer<GenericItem<T>> tableContainer = new BeanItemContainer<GenericItem<T>>(
				GenericItem.class);
		tableContainer.removeContainerProperty("element");
		setSelectable(true);
		setMultiSelect(false);
		setContainerDataSource(tableContainer);
		setColumnHeaders(new String[] {columnHeader});
		setSortContainerPropertyId(getContainerPropertyIds().iterator().next());
		setSortAscending(true);
	}
	
	@Override
	public void addActionHandler(Action.Handler actionHandler) {
		super.addActionHandler(actionHandler);
		if (actionHandler instanceof SingleActionHandler)
			actionHandlers.add((SingleActionHandler) actionHandler);
	}

	public List<SingleActionHandler> getActionHandlers()
	{
		return actionHandlers;
	}
	
	public void setInput(Collection<? extends T> types)
	{
		if (!isMultiSelect())
		{
			@SuppressWarnings("unchecked")
			GenericItem<T> selected = (GenericItem<T>) getValue();
			removeAllItems();
			for (T attributeType : types)
			{
				GenericItem<T> item = new GenericItem<T>(attributeType,
						nameProvider);
				addItem(item);
				if (selected != null && selected.getElement().equals(attributeType))
					setValue(item);
			}
		} else
		{
			@SuppressWarnings("unchecked")
			Collection<GenericItem<T>> selected = (Collection<GenericItem<T>>) getValue();
			removeAllItems();
			Collection<GenericItem<T>> nselected = new LinkedHashSet<GenericItem<T>>();
			for (T attributeType : types)
			{
				GenericItem<T> item = new GenericItem<T>(attributeType,
						nameProvider);
				addItem(item);
				for (GenericItem<T> s : selected)
				{
					if (s.getElement().equals(attributeType))
						nselected.add(item);
				}
			}
			setValue(nselected);
		}	
		sort();
	}
	
	public void addElement(T el)
	{
		addItem(new GenericItem<T>(el, nameProvider));
		sort();
	}
	
	public interface NameProvider<T>
	{
		/**
		 * @param element
		 * @return object of {@link Label} type or any other. In the latter case to toString method will be called 
		 * on the returned object, and the result will be wrapped as {@link Label}.
		 */
		public Object toRepresentation(T element);
	}

	public static class GenericItem<T>
	{
		private T element;
		private NameProvider<T> nameProvider;

		public GenericItem(T value, NameProvider<T> nameProvider)
		{
			this.element = value;
			this.nameProvider = nameProvider;
		}
		
		public Label getName()
		{
			Object representation = nameProvider.toRepresentation(element);
			if (representation instanceof Label)
				return (Label) representation;
			return new Label(representation.toString());
		}
		
		public T getElement()
		{
			return element;
		}
	}
	
	private static class DefaultNameProvider<T> implements NameProvider<T>
	{
		@Override
		public String toRepresentation(T element)
		{
			return element.toString();
		}
	}
}
