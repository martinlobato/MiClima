package martinlobato.com.ar.clima.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Páginas de Inicio: una por lugar. El id de cada página es el id del lugar (o
 * {@link HomeFragment#CURRENT_LOCATION} para la ubicación actual), así el ViewPager2 conserva
 * cada página aunque se agreguen o quiten lugares.
 */
public class PlacePagerAdapter extends FragmentStateAdapter {

    private final List<Long> ids = new ArrayList<>();

    public PlacePagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    public void setIds(List<Long> newIds) {
        ids.clear();
        ids.addAll(newIds);
        notifyDataSetChanged();
    }

    public long idAt(int position) {
        return ids.get(position);
    }

    public int positionOf(long id) {
        return ids.indexOf(id);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return HomeFragment.newInstance(ids.get(position));
    }

    @Override
    public int getItemCount() {
        return ids.size();
    }

    @Override
    public long getItemId(int position) {
        return ids.get(position);
    }

    @Override
    public boolean containsItem(long itemId) {
        return ids.contains(itemId);
    }
}
