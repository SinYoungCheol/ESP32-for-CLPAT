package ciel.android.libs.bluetooth;

import static ciel.android.libs.bluetooth.TaggedTicket.CertifiedResult.FormatError;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class TaggedTicketRecyclerViewAdapter extends RecyclerView.Adapter<TaggedTicketRecyclerViewAdapter.TaggedTicketVewHolder> {

    private List<TaggedTicket> mData;

    // 생성자
    public TaggedTicketRecyclerViewAdapter(ArrayList<TaggedTicket> items) {
        mData = items;
    }

/*
 *
    @Override
    public int getItemViewType(int position) {
        // return R.layout.tagged_tickets_item;
        return super.getItemViewType(position);
    }
*/
    @NonNull
    @Override
    public TaggedTicketVewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // TODO:
        //  (parent)는 (RecyclerView <R.id.tagged_ticket_recyclerview>)이고, 태그 (FrameLayout)에 포함되어 있다.
        //  (viewType)는 위에서 정의한 getItemViewType(int position)의 반환값이다.
        View itemView = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.tagged_tickets_item, parent, false);
        return new TaggedTicketVewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull TaggedTicketVewHolder holder, int position) {
        holder.bind(mData.get(position));
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void refresh() {
        notifyDataSetChanged();
    }

    public class TaggedTicketVewHolder extends RecyclerView.ViewHolder {
        public ImageView ivwCertified;
        public TextView tvPhoneNum;
        public TextView tvTaggedTime;

        public TaggedTicketVewHolder(@NonNull View itemView) {
            super(itemView);
            ivwCertified = itemView.findViewById(R.id.ivw_certified);
            tvPhoneNum = itemView.findViewById(R.id.tvw_phone_num);
            tvTaggedTime = itemView.findViewById(R.id.tvw_tagged_time);
        }

        public void bind(final TaggedTicket item) {
            if (item != null) {

                TaggedTicket.CertifiedResult value = item.getResult();
                switch(value) {
                    case Success:
                        ivwCertified.setImageResource(R.mipmap.ic_cert_success);
                        break;

                    case FormatError:
                    case DataSourceError:
                    case FailedOTP:
                    case NotMatchedDeviceCTN:
                    case AnyError:
                    default:
                        //ivCertified.setBackground(R.drawable.round_rectangle); // "@android:drawable/ic_dialog_alert"
                        ivwCertified.setImageResource(R.mipmap.ic_cert_error);
                        break;
                }

                tvPhoneNum.setText(item.getPhoneNum());
                tvTaggedTime.setText(item.getTaggedTime());
            }
        }

        @NonNull
        @Override
        public String toString(){
            return tvPhoneNum.getText() + " (" + (tvTaggedTime == null ? "no name" : tvTaggedTime.getText()) + ")";
        }
    } // END of Class ViewHolder
}
