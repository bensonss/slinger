package pl.allegro.android.slinger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static android.content.Intent.EXTRA_INITIAL_INTENTS;
import static android.content.Intent.createChooser;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;

/**
 * Starts Intent but without {@link Activity} that should be ignored.
 */
public class IntentStarter {
  public static final String RESOLVER_ACTIVITY = "com.android.internal.app.ResolverActivity";
  private final List<String> activitiesToIgnore;
  private final PackageManager packageManager;
  private final Intent intent;
  private final List<Intent> targetIntents = new ArrayList<>();
  private final String resolverTitle;
  private boolean wasResolved;

  public IntentStarter(@NonNull PackageManager packageManager, @NonNull Intent intent) {
    this(packageManager, intent, Collections.<Class<? extends Activity>>emptyList(), "");
  }

  public IntentStarter(@NonNull PackageManager packageManager, @NonNull Intent intent,
      @Nullable Class<? extends Activity> activityToIgnore, @Nullable String title) {
    this.intent = intent;
    this.packageManager = packageManager;

    this.activitiesToIgnore = activityToIgnore != null ? getActivitiesCanonicalNames(
        Collections.<Class<? extends Activity>>singletonList(activityToIgnore))
        : Collections.<String>emptyList();
    this.resolverTitle = title;
  }

  public IntentStarter(@NonNull PackageManager packageManager, @NonNull Intent intent,
      @Nullable List<Class<? extends Activity>> activitiesToIgnore, @Nullable String title) {
    this.intent = intent;
    this.packageManager = packageManager;
    this.activitiesToIgnore = getIgnoredActivitiesList(activitiesToIgnore);
    this.resolverTitle = title;
  }

  public IntentStarter(@NonNull PackageManager packageManager, @NonNull Intent intent,
      @Nullable List<Class<? extends Activity>> activitiesToIgnore) {
    this(packageManager, intent, activitiesToIgnore, null);
  }

  private List<String> getIgnoredActivitiesList(
      @Nullable List<Class<? extends Activity>> activitiesToIgnore) {
    if (activitiesToIgnore != null) {
      return getActivitiesCanonicalNames(activitiesToIgnore);
    }
    return Collections.emptyList();
  }

  private List<String> getActivitiesCanonicalNames(
      List<Class<? extends Activity>> activitiesToIgnore) {
    List<String> activityNames = new ArrayList<>();
    for (Class classObject : activitiesToIgnore) {
      activityNames.add(classObject.getCanonicalName());
    }
    return activityNames;
  }

  void resolveActivities() {
    if (wasResolved) {
      targetIntents.clear();
    }
    wasResolved = true;
    List<ResolveInfo> queryIntentActivities = packageManager.queryIntentActivities(intent, 0);

    for (ResolveInfo resolveInfo : queryIntentActivities) {
      PackageItemInfo resolvedActivityInfo = resolveInfo.activityInfo;
      if (!isActivityToBeIgnored(resolvedActivityInfo)) {

        if (queryIntentActivities.size() == 2 || resolveInfo.isDefault) {
          clearExistingAndAddDefaultIntent(resolvedActivityInfo);
          break;
        }

        addIntentWithExplicitPackageName(resolvedActivityInfo);
      }
    }
  }

  private void addIntentWithExplicitPackageName(PackageItemInfo resolvedActivityInfo) {
    targetIntents.add((new Intent(intent)).setPackage(resolvedActivityInfo.packageName));
  }

  private void clearExistingAndAddDefaultIntent(PackageItemInfo resolvedActivityInfo) {
    targetIntents.clear();
    targetIntents.add(0, (new Intent(intent)).setPackage(resolvedActivityInfo.packageName));
  }


  private boolean isActivityToBeIgnored(PackageItemInfo resolvedActivityInfo) {
    for (String activityToIgnore : activitiesToIgnore) {
      if (activityToIgnore.equals(resolvedActivityInfo.name)) {
        return true;
      }
    }
    return false;
  }

  List<Intent> getTargetIntents() {
    return Collections.unmodifiableList(targetIntents);
  }

  boolean hasDefaultHandler() {
    ActivityInfo resolvedActivityInfo =
        packageManager.resolveActivity(intent, MATCH_DEFAULT_ONLY).activityInfo;
    return !RESOLVER_ACTIVITY.equals(resolvedActivityInfo.name) && !isActivityToBeIgnored(
        resolvedActivityInfo);
  }

  public void startActivity(Activity parentActivity) {
    if (parentActivity == null) {
      return;
    }

    if (!wasResolved) {
      resolveActivities();
    }
    if (hasDefaultHandler()) {
      runDefaultActivity(parentActivity, intent);
    } else if (targetIntents.size() == 1) {
      runFirstAndOnlyOneActivity(parentActivity, targetIntents.get(0));
    } else {
      showChooser(parentActivity);
    }
  }

  private void runDefaultActivity(Context context, Intent mIntent) {
    context.startActivity(mIntent);
  }

  private void runFirstAndOnlyOneActivity(Context context, Intent intent) {
    context.startActivity(intent);
  }

  private void showChooser(Context context) {
    List<Intent> intentsList = getIntentList();

    Intent chooserIntent =
        createChooser(targetIntents.get(0), resolverTitle).putExtra(EXTRA_INITIAL_INTENTS,
            intentsList.toArray(new Parcelable[intentsList.size()]));
    context.startActivity(chooserIntent);
  }

  private List<Intent> getIntentList() {
    return targetIntents.subList(1, targetIntents.size());
  }
}