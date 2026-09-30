package com.mixcasete.app.player;

import com.mixcasete.app.data.Repository;
import com.mixcasete.app.util.SettingsStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class PlayerViewModel_Factory implements Factory<PlayerViewModel> {
  private final Provider<Repository> repoProvider;

  private final Provider<AudioEngine> engineProvider;

  private final Provider<SettingsStore> settingsStoreProvider;

  public PlayerViewModel_Factory(Provider<Repository> repoProvider,
      Provider<AudioEngine> engineProvider, Provider<SettingsStore> settingsStoreProvider) {
    this.repoProvider = repoProvider;
    this.engineProvider = engineProvider;
    this.settingsStoreProvider = settingsStoreProvider;
  }

  @Override
  public PlayerViewModel get() {
    return newInstance(repoProvider.get(), engineProvider.get(), settingsStoreProvider.get());
  }

  public static PlayerViewModel_Factory create(Provider<Repository> repoProvider,
      Provider<AudioEngine> engineProvider, Provider<SettingsStore> settingsStoreProvider) {
    return new PlayerViewModel_Factory(repoProvider, engineProvider, settingsStoreProvider);
  }

  public static PlayerViewModel newInstance(Repository repo, AudioEngine engine,
      SettingsStore settingsStore) {
    return new PlayerViewModel(repo, engine, settingsStore);
  }
}
