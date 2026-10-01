package com.mixcasete.app.di;

import com.mixcasete.app.data.db.AppDb;
import com.mixcasete.app.data.db.TrackDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class AppModule_ProvideTrackDaoFactory implements Factory<TrackDao> {
  private final Provider<AppDb> dbProvider;

  public AppModule_ProvideTrackDaoFactory(Provider<AppDb> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public TrackDao get() {
    return provideTrackDao(dbProvider.get());
  }

  public static AppModule_ProvideTrackDaoFactory create(Provider<AppDb> dbProvider) {
    return new AppModule_ProvideTrackDaoFactory(dbProvider);
  }

  public static TrackDao provideTrackDao(AppDb db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideTrackDao(db));
  }
}
