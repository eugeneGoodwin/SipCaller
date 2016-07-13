package com.test.caller;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ContactViewHolder> {

    private List<ContactData> contactList = new ArrayList<ContactData>();

    public class ContactViewHolder extends RecyclerView.ViewHolder {
        public TextView name, phone;

        public ContactViewHolder(View view) {
            super(view);
            name = (TextView) view.findViewById(R.id.contact_name);
            phone = (TextView) view.findViewById(R.id.contact_phone);
        }
    }

    private OnItemClickListener<ContactData> onItemClickListener;
    private OnItemLongClickListener<ContactData> onItemLongClickListener;

    public void setOnItemClickListener(OnItemClickListener<ContactData> onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener<ContactData> onItemLongClickListener) {
        this.onItemLongClickListener = onItemLongClickListener;
    }

    public void setDisplayContacts(List<ContactData> listContacts){
        this.contactList = listContacts;
    }

    @Override
    public ContactViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.contacts_layout, parent, false);

        return new ContactViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(ContactViewHolder holder, int position) {
        final ContactData contact = contactList.get(position);
        holder.name.setText(contact.getName());
        holder.phone.setText(contact.getPhone());
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onItemClickListener != null) {
                    onItemClickListener.onClick(contact);
                }
            }
        });
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return onItemLongClickListener != null && onItemLongClickListener.onLongClick(contact);
            }
        });
    }

    @Override
    public int getItemCount() {
        return contactList.size();
    }

    public interface OnItemClickListener<T> {
        void onClick(T t);
    }

    public interface OnItemLongClickListener<T> {
        boolean onLongClick(T t);
    }

}
